package com.example.makemovie.service;

import com.example.makemovie.client.LlmClient;
import com.example.makemovie.dto.response.StoryboardFrameResponse;
import com.example.makemovie.dto.response.StoryboardResponse;
import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Scene;
import com.example.makemovie.event.WorkflowEvent;
import com.example.makemovie.entity.Script;
import com.example.makemovie.entity.Storyboard;
import com.example.makemovie.entity.StoryboardFrame;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.SceneRepository;
import com.example.makemovie.repository.ScriptRepository;
import com.example.makemovie.repository.StoryboardFrameRepository;
import com.example.makemovie.repository.StoryboardRepository;
import com.example.makemovie.validation.JsonSchemaValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryboardService {

    private final LlmClient llmClient;
    private final StoryboardRepository storyboardRepository;
    private final StoryboardFrameRepository frameRepository;
    private final ScriptRepository scriptRepository;
    private final SceneRepository sceneRepository;
    private final CharacterRepository characterRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowLogService workflowLogService;
    private final ProgressService progressService;
    private final PromptLoader promptLoader;
    private final JsonSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final BackgroundGenerationService backgroundGenerationService;
    private final VideoGenPromptBuilder videoGenPromptBuilder;
    private final ImageStorageService imageStorageService;
    private final com.example.makemovie.repository.WorkflowLogRepository workflowLogRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    private static final String SCHEMA_PATH = "schemas/storyboard-output-schema.json";
    private static final int MAX_RETRY = 3;

    @Transactional
    public StoryboardResponse generateStoryboard(UUID projectId) {
        Script script = scriptRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("SCRIPT_NOT_FOUND", "请先生成剧本"));
        List<Character> characters = characterRepository.findByProjectId(projectId);
        if (characters.isEmpty()) {
            throw new BusinessException("NO_CHARACTERS", "请先生成角色人设");
        }

        String prompt = buildStoryboardPrompt(script, characters);

        // Save prompt to WorkflowLog
        workflowLogService.updateStatus(projectId, WorkflowStep.STORYBOARD_DESIGN,
                StepStatus.RUNNING,
                Map.of("scriptId", script.getId(), "characterCount", characters.size()),
                null, 0, prompt);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                long startTime = System.currentTimeMillis();
                String response = llmClient.generate(prompt);
                long elapsed = System.currentTimeMillis() - startTime;

                Set<ValidationMessage> errors = validateResponse(response);
                if (!errors.isEmpty()) {
                    log.warn("Storyboard validation failed (attempt {}): {}", attempt + 1, errors);
                    if (attempt < MAX_RETRY - 1) {
                        prompt = buildRetryPrompt(prompt, errors);
                        continue;
                    }
                    workflowLogService.markFailed(projectId,
                            WorkflowStep.STORYBOARD_DESIGN, "格式校验失败");
                    throw new BusinessException("STORYBOARD_VALIDATION_FAILED",
                            "分镜格式校验失败");
                }

                Map<String, Object> data = objectMapper.readValue(response,
                        new TypeReference<>() {});

                // Ensure Scene entities exist for mapping
                ensureScenesExist(script);
                StoryboardResponse result = saveStoryboard(projectId, script.getId(), data);

                eventPublisher.publishEvent(new WorkflowEvent.StepCompletedEvent(
                        this, projectId, "STORYBOARD_DESIGN",
                        Map.of("totalFrames", result.getTotalFrames()), elapsed, prompt));

                // Trigger next step: VOICE_GENERATION
                workflowLogService.updateStatus(projectId, WorkflowStep.VOICE_GENERATION,
                        StepStatus.RUNNING, null, null, 0);
                Project project = projectRepository.findById(projectId)
                        .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "项目不存在"));
                progressService.refreshProgress(project);

                log.info("Storyboard generated: projectId={}, totalFrames={}", projectId, result.getTotalFrames());

                // Generate scene backgrounds asynchronously via managed executor
                backgroundGenerationService.generateSceneBackgrounds(project, result.getId());

                // Generate per-frame video generation prompts
                generateFramePrompts(project, result.getId());

                return result;

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Storyboard generation error (attempt {})", attempt + 1, e);
                if (attempt == MAX_RETRY - 1) {
                    workflowLogService.markFailed(projectId,
                            WorkflowStep.STORYBOARD_DESIGN, e.getMessage());
                    throw new BusinessException("STORYBOARD_GEN_FAILED",
                            "分镜生成失败: " + e.getMessage());
                }
            }
        }
        throw new BusinessException("STORYBOARD_GEN_FAILED", "分镜生成失败");
    }

    public StoryboardResponse getStoryboard(UUID projectId) {
        Storyboard storyboard = storyboardRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("STORYBOARD_NOT_FOUND",
                        "该项目尚未生成分镜"));
        return buildResponse(storyboard);
    }

    /**
     * Update a single frame's editable fields.
     * If shotType or bgDescription changed, regenerates the clipPrompt.
     */
    @Transactional
    public StoryboardFrameResponse updateFrame(UUID frameId, Map<String, Object> updates) {
        StoryboardFrame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new BusinessException("FRAME_NOT_FOUND", "分镜帧不存在"));

        if (updates.containsKey("shotType")) frame.setShotType((String) updates.get("shotType"));
        if (updates.containsKey("cameraAngle")) frame.setCameraAngle((String) updates.get("cameraAngle"));
        if (updates.containsKey("subtitleText")) frame.setSubtitleText((String) updates.get("subtitleText"));
        if (updates.containsKey("durationSec"))
            frame.setDurationSec(((Number) updates.get("durationSec")).doubleValue());
        if (updates.containsKey("transition")) frame.setTransition((String) updates.get("transition"));
        if (updates.containsKey("bgDescription")) frame.setBgDescription((String) updates.get("bgDescription"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> characters =
                (List<Map<String, Object>>) updates.get("characters");
        if (characters != null) frame.setCharacters(characters);

        // Regenerate clipPrompt if key fields changed
        boolean promptStale = updates.containsKey("shotType")
                || updates.containsKey("bgDescription")
                || updates.containsKey("subtitleText")
                || updates.containsKey("characters");
        if (promptStale) {
            UUID projectId = storyboardRepository.findById(frame.getStoryboardId())
                    .map(Storyboard::getProjectId).orElse(null);
            if (projectId != null) {
                List<Character> characters2 = characterRepository.findByProjectId(projectId);
                Project project = projectRepository.findById(projectId).orElse(null);
                if (project != null) {
                    String newPrompt = videoGenPromptBuilder.buildPrompt(
                            frame, characters2, project.getTrack());
                    frame.setClipPrompt(newPrompt);
                    frame.setClipStatus("PROMPT_READY");
                }
            }
        }

        frameRepository.save(frame);
        return toFrameResponse(frame);
    }

    /**
     * Regenerate the entire storyboard (delete and recreate).
     */
    @Transactional
    public StoryboardResponse regenerateStoryboard(UUID projectId) {
        return generateStoryboard(projectId);
    }

    /**
     * Regenerate a single frame via LLM.
     */
    @Transactional
    public StoryboardFrameResponse regenerateFrame(UUID projectId, UUID frameId, String customPrompt) {
        StoryboardFrame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new BusinessException("FRAME_NOT_FOUND", "分镜帧不存在"));

        if (customPrompt != null && !customPrompt.isBlank()) {
            String response = llmClient.generate(customPrompt);
            try {
                Map<String, Object> data = objectMapper.readValue(response,
                        new TypeReference<>() {});
                if (data.containsKey("shotType")) frame.setShotType(toString(data.get("shotType")));
                if (data.containsKey("cameraAngle")) frame.setCameraAngle(toString(data.get("cameraAngle")));
                if (data.containsKey("bgDescription")) frame.setBgDescription(toString(data.get("bgDescription")));
                if (data.containsKey("characters")) frame.setCharacters(toList(data.get("characters")));
                if (data.containsKey("dialogueText")) frame.setSubtitleText(toString(data.get("dialogueText")));
            } catch (Exception e) {
                log.warn("Failed to parse LLM response for frame regenerate: {}", e.getMessage());
            }
        }

        // Regenerate clipPrompt
        UUID storyboardId = frame.getStoryboardId();
        UUID projId = storyboardRepository.findById(storyboardId)
                .map(Storyboard::getProjectId).orElse(null);
        if (projId != null) {
            List<Character> characters = characterRepository.findByProjectId(projId);
            Project project = projectRepository.findById(projId).orElse(null);
            if (project != null) {
                String newPrompt = videoGenPromptBuilder.buildPrompt(
                        frame, characters, project.getTrack());
                frame.setClipPrompt(newPrompt);
                frame.setClipStatus("PROMPT_READY");
            }
        }

        frameRepository.save(frame);
        return toFrameResponse(frame);
    }

    /**
     * Get the storyboard LLM prompt from WorkflowLog.
     */
    public Map<String, Object> getStoryboardPrompt(UUID projectId) {
        String prompt = workflowLogRepository
                .findByProjectIdAndStep(projectId, WorkflowStep.STORYBOARD_DESIGN)
                .map(log -> log.getPrompt())
                .orElse("");
        return Map.of("prompt", prompt != null ? prompt : "");
    }

    /**
     * Get the clip (video generation) prompt for a specific frame.
     */
    public Map<String, Object> getFramePrompt(UUID frameId) {
        StoryboardFrame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new BusinessException("FRAME_NOT_FOUND", "分镜帧不存在"));
        return Map.of(
                "clipPrompt", frame.getClipPrompt() != null ? frame.getClipPrompt() : "",
                "frameNumber", frame.getFrameNumber()
        );
    }

    private StoryboardResponse saveStoryboard(UUID projectId, UUID scriptId,
                                               Map<String, Object> data) {
        // Delete previous storyboard
        storyboardRepository.findByProjectId(projectId)
                .ifPresent(sb -> {
                    frameRepository.deleteAll(
                            frameRepository.findByStoryboardIdOrderByFrameNumber(sb.getId()));
                    storyboardRepository.delete(sb);
                });

        int totalFrames = ((Number) data.get("totalFrames")).intValue();

        Storyboard storyboard = Storyboard.builder()
                .projectId(projectId)
                .scriptId(scriptId)
                .totalFrames(totalFrames)
                .status("COMPLETED")
                .build();
        storyboard = storyboardRepository.save(storyboard);

        // Get scenes by script id for mapping
        List<Scene> scenes = sceneRepository.findByScriptIdOrderBySceneNumber(scriptId);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frames =
                (List<Map<String, Object>>) data.get("frames");

        List<StoryboardFrameResponse> frameResponses = new ArrayList<>();

        for (Map<String, Object> frameData : frames) {
            int sceneNum = ((Number) frameData.get("sceneNumber")).intValue();
            UUID sceneId = scenes.stream()
                    .filter(s -> s.getSceneNumber() == sceneNum)
                    .findFirst()
                    .map(Scene::getId)
                    .orElse(scenes.get(0).getId());

            StoryboardFrame frame = StoryboardFrame.builder()
                    .storyboardId(storyboard.getId())
                    .sceneId(sceneId)
                    .frameNumber(((Number) frameData.get("frameNumber")).intValue())
                    .shotType(toString(frameData.get("shotType")))
                    .cameraAngle(toString(frameData.get("cameraAngle")))
                    .bgDescription(toString(frameData.get("bgDescription")))
                    .characters(toList(frameData.get("characters")))
                    .subtitleText(toString(frameData.get("dialogueText")))
                    .durationSec(((Number) frameData.getOrDefault("durationSec", 3.0)).doubleValue())
                    .transition(toString(frameData.getOrDefault("transition", "cut")))
                    .status("COMPLETED")
                    .build();

            StoryboardFrame saved = frameRepository.save(frame);
            frameResponses.add(toFrameResponse(saved));
        }

        return StoryboardResponse.builder()
                .id(storyboard.getId())
                .projectId(projectId)
                .scriptId(scriptId)
                .totalFrames(totalFrames)
                .status("COMPLETED")
                .frames(frameResponses)
                .createdAt(storyboard.getCreatedAt())
                .build();
    }

    private StoryboardResponse buildResponse(Storyboard storyboard) {
        List<StoryboardFrame> frames =
                frameRepository.findByStoryboardIdOrderByFrameNumber(storyboard.getId());

        return StoryboardResponse.builder()
                .id(storyboard.getId())
                .projectId(storyboard.getProjectId())
                .scriptId(storyboard.getScriptId())
                .totalFrames(storyboard.getTotalFrames())
                .status(storyboard.getStatus())
                .frames(frames.stream().map(this::toFrameResponse).toList())
                .createdAt(storyboard.getCreatedAt())
                .build();
    }

    private StoryboardFrameResponse toFrameResponse(StoryboardFrame frame) {
        return StoryboardFrameResponse.builder()
                .id(frame.getId())
                .sceneId(frame.getSceneId())
                .frameNumber(frame.getFrameNumber())
                .shotType(frame.getShotType())
                .cameraAngle(frame.getCameraAngle())
                .bgDescription(frame.getBgDescription())
                .bgImageUrl(frame.getBgImageUrl())
                .characters(frame.getCharacters())
                .dialogueId(frame.getDialogueId())
                .subtitleText(frame.getSubtitleText())
                .durationSec(frame.getDurationSec())
                .transition(frame.getTransition())
                .status(frame.getStatus())
                .clipPrompt(frame.getClipPrompt())
                .clipStatus(frame.getClipStatus())
                .clipVideoUrl(frame.getClipVideoUrl())
                .build();
    }

    private void ensureScenesExist(Script script) {
        List<Scene> existing = sceneRepository.findByScriptIdOrderBySceneNumber(script.getId());
        if (!existing.isEmpty()) return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sceneList =
                (List<Map<String, Object>>) script.getContent().get("scenes");
        if (sceneList == null || sceneList.isEmpty()) return;

        for (Map<String, Object> sceneData : sceneList) {
            int num = ((Number) sceneData.get("sceneNumber")).intValue();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dialogues =
                    (List<Map<String, Object>>) sceneData.getOrDefault("dialogues", List.of());
            Scene scene = Scene.builder()
                    .scriptId(script.getId())
                    .sceneNumber(num)
                    .location((String) sceneData.getOrDefault("location", ""))
                    .timeOfDay((String) sceneData.getOrDefault("timeOfDay", ""))
                    .summary((String) sceneData.getOrDefault("summary", ""))
                    .dialogues(dialogues)
                    .durationEstimate(((Number) sceneData.getOrDefault("durationEstimate", 0)).intValue())
                    .build();
            sceneRepository.save(scene);
        }
        log.info("Created {} scene entities for script {}", sceneList.size(), script.getId());
    }

    private String buildStoryboardPrompt(Script script, List<Character> characters) {
        String schemaStr = loadSchemaString();

        // Build detailed character info including full appearance
        StringBuilder charDetails = new StringBuilder();
        for (Character c : characters) {
            charDetails.append(String.format("- %s (%s): %s\n", c.getName(), c.getRole(), c.getPersonality()));
            Map<String, Object> appearance = c.getAppearance();
            if (appearance != null && !appearance.isEmpty()) {
                charDetails.append("  外貌: ");
                if (appearance.containsKey("hairStyle"))
                    charDetails.append(appearance.get("hairStyle")).append("发型, ");
                if (appearance.containsKey("hairColor"))
                    charDetails.append(appearance.get("hairColor")).append("发色, ");
                if (appearance.containsKey("eyeColor"))
                    charDetails.append(appearance.get("eyeColor")).append("瞳色, ");
                if (appearance.containsKey("height"))
                    charDetails.append(appearance.get("height")).append(", ");
                if (appearance.containsKey("clothing"))
                    charDetails.append("穿着").append(appearance.get("clothing")).append(", ");
                if (appearance.containsKey("features"))
                    charDetails.append("特征: ").append(appearance.get("features"));
                charDetails.append("\n");
            }
        }

        Map<String, String> vars = Map.of(
                "scriptContent", objectMapper.valueToTree(script.getContent()).toPrettyString(),
                "characterInfo", charDetails.toString()
        );
        String prompt = promptLoader.load("storyboard-design", vars);
        return prompt + "\n\n请严格按照以下JSON Schema输出：\n" + schemaStr;
    }

    private String buildRetryPrompt(String originalPrompt, Set<ValidationMessage> errors) {
        return originalPrompt + "\n\n上次输出的JSON格式有误，请修正:\n" + errors;
    }

    private Set<ValidationMessage> validateResponse(String response) {
        String schema = loadSchemaString();
        return schemaValidator.validate(schema, response);
    }

    private String loadSchemaString() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(SCHEMA_PATH)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("SCHEMA_LOAD_FAILED", "无法加载JSON Schema");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toList(Object val) {
        if (val instanceof List) return (List<Map<String, Object>>) val;
        return List.of();
    }

    private String toString(Object val) {
        return val != null ? val.toString() : "";
    }

    /**
     * Generate video generation prompts for all frames in this storyboard.
     * Saves each prompt to StoryboardFrame.clipPrompt and to file storage.
     */
    private void generateFramePrompts(Project project, UUID storyboardId) {
        try {
            List<StoryboardFrame> frames = frameRepository
                    .findByStoryboardIdOrderByFrameNumber(storyboardId);
            List<Character> characters = characterRepository.findByProjectId(project.getId());

            for (StoryboardFrame frame : frames) {
                String prompt = videoGenPromptBuilder.buildPrompt(
                        frame, characters, project.getTrack());

                frame.setClipPrompt(prompt);
                frame.setClipStatus("PROMPT_READY");
                frameRepository.save(frame);

                // Save prompt to project file structure
                String promptPath = String.format("03-storyboard/prompts/frame_%03d.txt",
                        frame.getFrameNumber());
                imageStorageService.storeProjectText(
                        project.getId(), promptPath, prompt);
            }
            log.info("Generated video prompts for {} frames in project {}",
                    frames.size(), project.getId());
        } catch (Exception e) {
            log.error("Failed to generate frame prompts for project {}: {}",
                    project.getId(), e.getMessage());
        }
    }
}
