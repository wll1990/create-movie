package com.example.makemovie.service;

import com.example.makemovie.client.ImageGenClient;
import com.example.makemovie.client.LlmClient;
import com.example.makemovie.dto.response.CopywritingResponse;
import com.example.makemovie.entity.Composition;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Script;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CompositionRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.ScriptRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CopywritingService {

    private final LlmClient llmClient;
    private final ImageGenClient imageGenClient;
    private final ImageStorageService imageStorageService;
    private final ProjectRepository projectRepository;
    private final ScriptRepository scriptRepository;
    private final CompositionRepository compositionRepository;
    private final WorkflowLogService workflowLogService;
    private final ProgressService progressService;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    private static final int MAX_RETRY = 2;

    @Transactional
    public CopywritingResponse generateCopywriting(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "项目不存在"));

        Script script = scriptRepository.findByProjectId(projectId)
                .orElse(null);
        List<Composition> compositions = compositionRepository.findByProjectId(projectId);
        Composition composition = compositions.isEmpty() ? null : compositions.get(0);

        workflowLogService.updateStatus(projectId, WorkflowStep.COPYWRITING,
                StepStatus.RUNNING, null, null, 0);

        String prompt = buildPrompt(project, script, composition);

        // Save prompt to WorkflowLog
        workflowLogService.updateStatus(projectId, WorkflowStep.COPYWRITING,
                StepStatus.RUNNING, null, null, 0, prompt);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                long startTime = System.currentTimeMillis();
                String response = llmClient.generate(prompt);
                long elapsed = System.currentTimeMillis() - startTime;

                Map<String, Object> data = objectMapper.readValue(response,
                        new TypeReference<>() {});

                @SuppressWarnings("unchecked")
                List<String> hashtags = (List<String>) data.getOrDefault("hashtags", List.of());

                CopywritingResponse result = CopywritingResponse.builder()
                        .title((String) data.getOrDefault("title", script != null ? script.getTitle() : ""))
                        .description((String) data.getOrDefault("description", ""))
                        .hashtags(hashtags)
                        .coverDescription((String) data.getOrDefault("coverDescription", ""))
                        .build();

                // Generate cover image from coverDescription (non-blocking submit)
                String coverUrl = null;
                if (result.getCoverDescription() != null && !result.getCoverDescription().isBlank()) {
                    try {
                        String coverPrompt = "【封面图】\n动漫风格，" + result.getCoverDescription()
                                + "\n要求：高质量，9:16构图，适合短视频平台发布";
                        String coverTaskId = imageGenClient.submitTask(
                                imageGenClient.buildImageBody(coverPrompt, "1080*1920"));
                        if (coverTaskId != null) {
                            log.info("Cover image task submitted: {}", coverTaskId);
                            // Cover image result handled by ImageGenPoller BACKGROUND callback
                            // Store to composition later if needed
                        }
                    } catch (Exception e) {
                        log.warn("Cover image task submission failed: {}", e.getMessage());
                    }
                }

                // Save cover URL to composition
                if (coverUrl != null && composition != null) {
                    composition.setCoverUrl(coverUrl);
                    compositionRepository.save(composition);
                }

                workflowLogService.updateStatus(projectId, WorkflowStep.COPYWRITING,
                        StepStatus.COMPLETED,
                        Map.of("projectTitle", project.getTitle(),
                                "track", project.getTrack()),
                        Map.of("title", result.getTitle(),
                                "hashtags", result.getHashtags()),
                        elapsed, prompt);

                project.setStatus("COMPLETED");
                projectRepository.save(project);
                progressService.refreshProgress(project);

                log.info("Copywriting generated: projectId={}, title={}", projectId, result.getTitle());
                return result;

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Copywriting generation error (attempt {})", attempt + 1, e);
                if (attempt == MAX_RETRY - 1) {
                    workflowLogService.markFailed(projectId,
                            WorkflowStep.COPYWRITING, e.getMessage());
                    throw new BusinessException("COPYWRITING_FAILED",
                            "文案生成失败: " + e.getMessage());
                }
            }
        }
        throw new BusinessException("COPYWRITING_FAILED", "文案生成失败");
    }

    private String buildPrompt(Project project, Script script, Composition composition) {
        String scriptSummary = "";
        if (script != null && script.getContent() != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> scenes =
                    (List<Map<String, Object>>) script.getContent().get("scenes");
            if (scenes != null && !scenes.isEmpty()) {
                Map<String, Object> firstScene = scenes.get(0);
                scriptSummary = "开场: " + firstScene.getOrDefault("summary", "");
                if (scenes.size() > 1) {
                    Map<String, Object> lastScene = scenes.get(scenes.size() - 1);
                    scriptSummary += " | 结尾: " + lastScene.getOrDefault("summary", "");
                }
            }
        }

        String videoInfo = composition != null && composition.getVideoUrl() != null
                ? "视频已生成，时长: " + composition.getDurationSec() + "秒"
                : "视频待生成";

        return promptLoader.load("copywriting", Map.of(
                "projectTitle", project.getTitle(),
                "track", project.getTrack() != null ? project.getTrack() : "未知",
                "scriptSummary", scriptSummary.isEmpty() ? "待生成" : scriptSummary,
                "videoInfo", videoInfo
        ));
    }

    private byte[] downloadBytes(String url) {
        if (url == null || url.isBlank()) return new byte[0];
        try (java.io.InputStream is = java.net.URI.create(url).toURL().openStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to download from {}: {}", url, e.getMessage());
            return new byte[0];
        }
    }
}
