package com.example.makemovie.service;

import com.example.makemovie.client.LlmClient;
import com.example.makemovie.client.SpeechToTextClient;
import com.example.makemovie.dto.response.VideoGeneResponse;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.VideoGene;
import com.example.makemovie.enums.ProjectMode;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.VideoGeneRepository;
import com.example.makemovie.validation.JsonSchemaValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAnalyzerService {

    private final LlmClient llmClient;
    private final SpeechToTextClient sttClient;
    private final VideoGeneRepository geneRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowLogService workflowLogService;
    private final ProgressService progressService;
    private final GeneToTemplateMapper geneToTemplateMapper;
    private final JsonSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final PromptLoader promptLoader;

    @Value("${storage.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${storage.temp-dir:./temp}")
    private String tempDir;

    private static final String SCHEMA_PATH = "schemas/video-gene-output-schema.json";
    private static final int MAX_RETRY = 3;

    /**
     * Analyze a video file to extract its "VideoGene" across 4 dimensions.
     *
     * Pipeline:
     * 1. Save uploaded video
     * 2. FFmpeg extract keyframes
     * 3. FFmpeg extract audio → STT transcript
     * 4. LLM analysis → VideoGene JSON
     * 5. Validate, save, update progress
     */
    @Transactional
    public VideoGeneResponse analyze(UUID projectId, MultipartFile videoFile) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "项目不存在"));

        if (project.getMode() != ProjectMode.ANALYSIS && project.getMode() != ProjectMode.HYBRID) {
            throw new BusinessException("WRONG_MODE",
                    "只有分析模式或混合模式才能进行视频分析");
        }

        workflowLogService.updateStatus(projectId, WorkflowStep.TOPIC_DESIGN,
                StepStatus.RUNNING, Map.of("fileName", videoFile.getOriginalFilename()), null, 0);

        try {
            // Step 1: Save video
            Path videoPath = saveVideo(projectId, videoFile);

            // Step 2: Extract keyframes
            Path frameDir = Path.of(tempDir, "keyframes", projectId.toString());
            List<String> framePaths = extractKeyframes(videoPath, frameDir);

            // Step 3: Extract audio + transcribe
            Path audioPath = Path.of(tempDir, "audio", projectId + ".wav");
            extractAudio(videoPath, audioPath);
            String transcript = sttClient.transcribe(audioPath.toString());
            if (transcript == null || transcript.isBlank()) {
                transcript = "[音频转录不可用]";
            }

            // Step 4: LLM analysis with retry
            String prompt = buildAnalysisPrompt(project.getTrack(),
                    framePaths.size(), transcript);

            for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
                try {
                    long startTime = System.currentTimeMillis();
                    String response = llmClient.generateWithImages(prompt, framePaths);
                    long elapsed = System.currentTimeMillis() - startTime;

                    Set<ValidationMessage> errors = validateResponse(response);
                    if (!errors.isEmpty()) {
                        log.warn("Gene validation failed (attempt {}): {}", attempt + 1, errors);
                        if (attempt < MAX_RETRY - 1) {
                            prompt = buildRetryPrompt(prompt, errors);
                            continue;
                        }
                        throw new BusinessException("GENE_VALIDATION_FAILED",
                                "视频基因格式校验失败");
                    }

                    Map<String, Object> data = objectMapper.readValue(response,
                            new TypeReference<>() {});

                    VideoGene gene = saveGene(projectId, data);

                    workflowLogService.updateStatus(projectId, WorkflowStep.TOPIC_DESIGN,
                            StepStatus.COMPLETED,
                            Map.of("framesExtracted", framePaths.size(),
                                    "transcriptLen", transcript.length()),
                            data, elapsed);

                    progressService.refreshProgress(project);

                    // If HYBRID mode, auto-generate CreationTemplate
                    if (project.getMode() == ProjectMode.HYBRID) {
                        geneToTemplateMapper.createTemplate(gene.getId(), "默认");
                    }

                    log.info("Video gene extracted: projectId={}, track={}",
                            projectId, gene.getTrack());
                    return VideoGeneResponse.fromEntity(gene);

                } catch (BusinessException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Gene analysis error (attempt {})", attempt + 1, e);
                    if (attempt == MAX_RETRY - 1) {
                        workflowLogService.markFailed(projectId,
                                WorkflowStep.TOPIC_DESIGN, e.getMessage());
                        throw new BusinessException("ANALYSIS_FAILED",
                                "视频分析失败: " + e.getMessage());
                    }
                }
            }

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Video analysis pipeline failed", e);
            workflowLogService.markFailed(projectId,
                    WorkflowStep.TOPIC_DESIGN, e.getMessage());
            throw new BusinessException("ANALYSIS_FAILED", "视频分析失败: " + e.getMessage());
        }

        throw new BusinessException("ANALYSIS_FAILED", "视频分析失败");
    }

    public VideoGeneResponse getGene(UUID projectId) {
        VideoGene gene = geneRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("GENE_NOT_FOUND", "未找到分析结果"));
        return VideoGeneResponse.fromEntity(gene);
    }

    // --- Pipeline steps ---

    private Path saveVideo(UUID projectId, MultipartFile file) throws IOException {
        Path dir = Path.of(uploadDir, projectId.toString());
        Files.createDirectories(dir);
        Path videoPath = dir.resolve(Objects.requireNonNull(
                file.getOriginalFilename(), "video.mp4"));
        file.transferTo(videoPath.toFile());
        log.info("Video saved: {}", videoPath);
        return videoPath;
    }

    private List<String> extractKeyframes(Path videoPath, Path frameDir)
            throws IOException, InterruptedException {
        Files.createDirectories(frameDir);

        List<String> command = List.of(
                "ffmpeg", "-y",
                "-i", videoPath.toString(),
                "-vf", "select=gt(scene\\,0.3),scale=720:-1",
                "-vsync", "vfr",
                "-frames:v", "30",
                frameDir.resolve("frame_%04d.jpg").toString()
        );

        runFfmpeg(command, "keyframe extraction");

        List<String> paths = new ArrayList<>();
        File[] files = frameDir.toFile().listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().endsWith(".jpg")) {
                    paths.add(f.getAbsolutePath());
                }
            }
        }
        log.info("Extracted {} keyframes to {}", paths.size(), frameDir);
        return paths;
    }

    private void extractAudio(Path videoPath, Path audioPath)
            throws IOException, InterruptedException {
        Files.createDirectories(audioPath.getParent());

        List<String> command = List.of(
                "ffmpeg", "-y",
                "-i", videoPath.toString(),
                "-vn",
                "-acodec", "pcm_s16le",
                "-ar", "16000",
                "-ac", "1",
                audioPath.toString()
        );

        runFfmpeg(command, "audio extraction");
        log.info("Audio extracted: {} ({} bytes)", audioPath, Files.size(audioPath));
    }

    private void runFfmpeg(List<String> command, String step)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // consume output
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            log.warn("FFmpeg {} exited with code {}", step, exitCode);
        }
    }

    // --- LLM interaction ---

    private String buildAnalysisPrompt(String track, int frameCount, String transcript) {
        String schemaStr = loadSchemaString();
        return promptLoader.load("video-analysis", Map.of(
                "track", track != null ? track : "未知",
                "frameCount", String.valueOf(frameCount),
                "transcript", transcript.substring(0, Math.min(transcript.length(), 2000)),
                "schemaStr", schemaStr
        ));
    }

    private String buildRetryPrompt(String originalPrompt, Set<ValidationMessage> errors) {
        return originalPrompt + "\n\n上次输出格式有误，请修正:\n" + errors;
    }

    private Set<ValidationMessage> validateResponse(String response) {
        String schema = loadSchemaString();
        return schemaValidator.validate(schema, response);
    }

    private String loadSchemaString() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(SCHEMA_PATH)) {
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("SCHEMA_LOAD_FAILED", "无法加载JSON Schema");
        }
    }

    private VideoGene saveGene(UUID projectId, Map<String, Object> data) {
        // Delete existing
        geneRepository.findByProjectId(projectId)
                .ifPresent(geneRepository::delete);

        @SuppressWarnings("unchecked")
        Map<String, Object> contentGene = (Map<String, Object>) data.get("contentGene");
        @SuppressWarnings("unchecked")
        Map<String, Object> visualGene = (Map<String, Object>) data.get("visualGene");
        @SuppressWarnings("unchecked")
        Map<String, Object> audioGene = (Map<String, Object>) data.get("audioGene");
        @SuppressWarnings("unchecked")
        Map<String, Object> trafficGene = (Map<String, Object>) data.get("trafficGene");
        String track = (String) data.getOrDefault("track", "");

        VideoGene gene = VideoGene.builder()
                .projectId(projectId)
                .track(track)
                .contentGene(contentGene != null ? contentGene : Map.of())
                .visualGene(visualGene != null ? visualGene : Map.of())
                .audioGene(audioGene != null ? audioGene : Map.of())
                .trafficGene(trafficGene != null ? trafficGene : Map.of())
                .build();

        return geneRepository.save(gene);
    }
}
