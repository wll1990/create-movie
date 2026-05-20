package com.example.makemovie.service;

import com.example.makemovie.client.ImageGenClient;
import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.ImageGenTask;
import com.example.makemovie.entity.Project;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.event.ImageTaskCompletedEvent;
import com.example.makemovie.event.WorkflowEvent;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.ImageGenTaskRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Character image generation — non-blocking task-based flow.
 *
 * Instead of blocking threads with Thread.sleep polling, we:
 * 1. Submit image gen tasks to 通义万相 (returns taskId immediately)
 * 2. Save ImageGenTask entities to DB
 * 3. ImageGenPoller (@Scheduled) picks up DB tasks → publishes ImageTaskCompletedEvent
 * 4. CharacterImageService @EventListener handles the event → updates character
 *
 * Flow:
 * Character saved → generateCandidates() async → submit 4 portrait tasks → return
 * User selects portrait → selectPortrait() → submit 1 threeview task → return
 * Threeview done (callback) → submit 15 expression tasks (5 emotions × 3) → return
 * User selects expressions → selectExpression() → check all complete → publish event
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterImageService {

    private final ImageGenClient imageGenClient;
    private final ImageGenTaskRepository taskRepository;
    private final ImageStorageService imageStorageService;
    private final CharacterRepository characterRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowLogService workflowLogService;
    private final ProgressService progressService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    private static final int PORTRAIT_CANDIDATE_COUNT = 4;
    private static final int EXPRESSION_CANDIDATE_COUNT = 3;
    private static final String[] EXPRESSION_TYPES = {"neutral", "happy", "sad", "surprised", "angry"};

    private CharacterImageService self() {
        return applicationContext.getBean(CharacterImageService.class);
    }

    // ============================================================
    // Phase 1: Portrait Candidates (立绘抽卡)
    // ============================================================

    @Async("backgroundGenExecutor")
    public void generateCandidates(UUID projectId, UUID charId, String name, String appearanceDesc) {
        log.info("Submitting {} portrait tasks for {} (id={})", PORTRAIT_CANDIDATE_COUNT, name, charId);

        String[] variations = {
                "正面立绘，标准站姿，白色背景",
                "正面立绘，微微侧身，动态姿势，白色背景",
                "正面立绘，双手自然垂放，白色背景",
                "正面立绘，略带表情，自然姿态，白色背景"
        };

        for (int i = 0; i < PORTRAIT_CANDIDATE_COUNT; i++) {
            String prompt = imageGenClient.buildPortraitPrompt(name, appearanceDesc, variations[i]);
            String taskId = imageGenClient.submitTask(imageGenClient.buildImageBody(prompt, "1024*1024"));
            if (taskId != null) {
                ImageGenTask task = ImageGenTask.builder()
                        .projectId(projectId).charId(charId)
                        .taskType("PORTRAIT").externalTaskId(taskId)
                        .prompt(prompt).callbackKey(name + ":" + i)
                        .status("PENDING").build();
                taskRepository.save(task);
                // Poller picks up from DB on next tick
            }
        }
    }

    // ============================================================
    // Phase 2: User selects portrait → triggers three-view
    // ============================================================

    @Transactional
    public void selectPortrait(UUID projectId, UUID charId, int candidateIndex) {
        Character character = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));

        if (candidateIndex < 0 || candidateIndex >= character.getCandidatePortraits().size()) {
            throw new BusinessException("INVALID_CANDIDATE", "候选立绘索引无效");
        }

        Map<String, Object> selected = character.getCandidatePortraits().get(candidateIndex);
        String ossUrl = (String) selected.getOrDefault("ossUrl", selected.get("localUrl"));
        String localUrl = (String) selected.getOrDefault("localUrl", ossUrl);

        Map<String, Object> enrichedAppearance = new LinkedHashMap<>(character.getAppearance());
        enrichedAppearance.put("portraitUrl", ossUrl);
        enrichedAppearance.put("portraitLocalUrl", localUrl);
        character.setAppearance(enrichedAppearance);
        character.setSelectedPortraitIndex(candidateIndex);
        character.setImageGenerationStatus("THREEVIEW_GENERATING");
        characterRepository.save(character);

        String appearanceDesc = buildAppearanceDescription(character);
        self().submitThreeViewTask(projectId, charId, character.getName(), appearanceDesc, ossUrl);

        log.info("Character {} (id={}): portrait selected, threeview task submitted",
                character.getName(), charId);
    }

    @Async("backgroundGenExecutor")
    public void submitThreeViewTask(UUID projectId, UUID charId, String name,
                                     String appearanceDesc, String referenceOssUrl) {
        String prompt = imageGenClient.buildThreeViewPrompt(name, appearanceDesc);
        Map<String, Object> body = imageGenClient.buildRefImageBody(prompt, referenceOssUrl, "1280*720");
        String taskId = imageGenClient.submitTask(body);
        if (taskId != null) {
            ImageGenTask task = ImageGenTask.builder()
                    .projectId(projectId).charId(charId)
                    .taskType("THREEVIEW").externalTaskId(taskId)
                    .prompt(prompt).callbackKey(name)
                    .status("PENDING").build();
            taskRepository.save(task);
            // Picked up by ImageGenPoller from DB
        }
    }

    // ============================================================
    // Phase 3: Expression Candidates (submit tasks, non-blocking)
    // ============================================================

    @Async("backgroundGenExecutor")
    public void submitExpressionTasks(UUID projectId, UUID charId, String name,
                                       String appearanceDesc, String referenceOssUrl) {
        log.info("Submitting expression tasks for {} (id={})", name, charId);

        Character character = characterRepository.findById(charId).orElse(null);
        if (character == null) return;

        String[] poseVariations = {"正面特写，自然光线", "微侧脸特写，柔和光线", "正面半身，自然姿态"};

        Map<String, String> exprLabels = Map.of(
                "neutral", "自然平静", "happy", "开心微笑", "sad", "悲伤难过",
                "surprised", "惊讶", "angry", "生气愤怒");

        character.setImageGenerationStatus("EXPRESSIONS_GENERATING");
        character.setExpressions(new ArrayList<>());
        characterRepository.save(character);

        // Batch submissions to avoid rate limiting (3 parallel, 2s gap between batches)
        int batchSize = 3;
        int submitted = 0;
        for (String expr : EXPRESSION_TYPES) {
            String exprLabel = exprLabels.getOrDefault(expr, expr);
            for (int i = 0; i < EXPRESSION_CANDIDATE_COUNT; i++) {
                String prompt = imageGenClient.buildExpressionPrompt(
                        name, expr, appearanceDesc, poseVariations[i], exprLabel);
                Map<String, Object> body = imageGenClient.buildRefImageBody(prompt, referenceOssUrl, "1024*1024");
                String taskId = imageGenClient.submitTask(body);
                if (taskId != null) {
                    ImageGenTask task = ImageGenTask.builder()
                            .projectId(projectId).charId(charId)
                            .taskType("EXPRESSION").externalTaskId(taskId)
                            .prompt(prompt).callbackKey(name + ":" + expr + ":" + i)
                            .status("PENDING").build();
                    taskRepository.save(task);
                }
                submitted++;
                if (submitted % batchSize == 0) {
                    try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        log.info("Submitted {} expression tasks for {} (id={})", submitted, name, charId);
    }

    // ============================================================
    // Event listener: ImageGenPoller publishes ImageTaskCompletedEvent
    // ============================================================

    @EventListener
    @Transactional
    public void onImageTaskEvent(ImageTaskCompletedEvent event) {
        ImageGenTask task = event.getTask();
        String persistentUrl = event.getPersistentUrl();
        if (persistentUrl != null) {
            onImageTaskCompleted(task, persistentUrl);
        } else {
            onImageTaskFailed(task);
        }
    }

    // ============================================================
    // Internal callbacks
    // ============================================================

    @Transactional
    public void onImageTaskCompleted(ImageGenTask task, String persistentUrl) {
        switch (task.getTaskType()) {
            case "PORTRAIT" -> onPortraitCompleted(task, persistentUrl);
            case "THREEVIEW" -> onThreeViewCompleted(task, persistentUrl);
            case "EXPRESSION" -> onExpressionCompleted(task, persistentUrl);
        }
    }

    @Transactional
    public void onImageTaskFailed(ImageGenTask task) {
        if (task.getCharId() == null) return;
        Character character = characterRepository.findById(task.getCharId()).orElse(null);
        if (character == null) return;

        log.warn("Image task failed: type={}, char={}", task.getTaskType(), character.getName());

        if ("THREEVIEW".equals(task.getTaskType())) {
            character.setImageGenerationStatus("THREEVIEW_FAILED");
            characterRepository.save(character);
        }
    }

    private void onPortraitCompleted(ImageGenTask task, String persistentUrl) {
        if (task.getCharId() == null) return;
        Character character = characterRepository.findById(task.getCharId()).orElse(null);
        if (character == null) return;

        // Parse callback key "name:index"
        String callbackKey = task.getCallbackKey();
        int colonIdx = callbackKey.lastIndexOf(':');
        int index = Integer.parseInt(callbackKey.substring(colonIdx + 1));

        List<Map<String, Object>> candidates = new ArrayList<>(character.getCandidatePortraits());
        candidates.add(Map.of(
                "index", index,
                "localUrl", persistentUrl,
                "ossUrl", persistentUrl.startsWith("https://") ? persistentUrl : "",
                "prompt", task.getPrompt() != null ? task.getPrompt() : ""
        ));
        character.setCandidatePortraits(candidates);

        if (candidates.size() >= PORTRAIT_CANDIDATE_COUNT) {
            character.setImageGenerationStatus("CANDIDATES_READY");
        }
        characterRepository.save(character);
        log.info("Portrait candidate {}/{} for {} completed", candidates.size(), PORTRAIT_CANDIDATE_COUNT, character.getName());
    }

    private void onThreeViewCompleted(ImageGenTask task, String persistentUrl) {
        if (task.getCharId() == null) return;
        Character character = characterRepository.findById(task.getCharId()).orElse(null);
        if (character == null) return;

        Map<String, Object> appearance = new LinkedHashMap<>(character.getAppearance());
        appearance.put("threeViewUrl", persistentUrl);
        appearance.put("threeViewLocalUrl", persistentUrl);
        appearance.put("referenceImageUrl", persistentUrl);
        character.setAppearance(appearance);
        character.setImageGenerationStatus("EXPRESSIONS_GENERATING");
        characterRepository.save(character);

        log.info("Three-view complete for {} (id={}), submitting expression tasks", character.getName(), task.getCharId());

        String referenceOssUrl = (String) character.getAppearance().get("portraitUrl");
        String appearanceDesc = buildAppearanceDescription(character);
        self().submitExpressionTasks(task.getProjectId(), task.getCharId(),
                character.getName(), appearanceDesc, referenceOssUrl);
    }

    private void onExpressionCompleted(ImageGenTask task, String persistentUrl) {
        if (task.getCharId() == null) return;
        Character character = characterRepository.findById(task.getCharId()).orElse(null);
        if (character == null) return;

        // Parse callback key "name:emotion:index"
        String callbackKey = task.getCallbackKey();
        String[] parts = callbackKey.split(":");
        if (parts.length < 3) return;
        String emotion = parts[1];
        int index = Integer.parseInt(parts[2]);

        List<Map<String, Object>> expressions = new ArrayList<>(character.getExpressions());
        Map<String, Object> exprEntry = null;
        for (Map<String, Object> e : expressions) {
            if (emotion.equals(e.get("type"))) { exprEntry = e; break; }
        }
        if (exprEntry == null) {
            exprEntry = new LinkedHashMap<>();
            exprEntry.put("type", emotion);
            exprEntry.put("selectedIndex", 0);
            exprEntry.put("candidates", new ArrayList<>());
            expressions.add(exprEntry);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) exprEntry.get("candidates");
        candidates.add(Map.of(
                "index", index,
                "localUrl", persistentUrl,
                "ossUrl", persistentUrl.startsWith("https://") ? persistentUrl : "",
                "prompt", task.getPrompt() != null ? task.getPrompt() : ""
        ));

        character.setExpressions(expressions);

        // Check if all expression candidates are done
        long totalCandidates = expressions.stream()
                .mapToLong(e -> {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> cands = (List<Map<String, Object>>) e.get("candidates");
                    return cands != null ? cands.size() : 0;
                }).sum();
        long expected = EXPRESSION_TYPES.length * EXPRESSION_CANDIDATE_COUNT;

        if (totalCandidates >= expected) {
            // All expression candidates received
            boolean allHaveCandidates = expressions.stream()
                    .allMatch(e -> {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> cands = (List<Map<String, Object>>) e.get("candidates");
                        return cands != null && cands.size() >= EXPRESSION_CANDIDATE_COUNT;
                    });
            if (allHaveCandidates) {
                character.setImageGenerationStatus("EXPRESSIONS_READY");
            }
        }

        characterRepository.save(character);
    }

    // ============================================================
    // Phase 4: User selects expression per emotion
    // ============================================================

    @Transactional
    public void selectExpression(UUID projectId, UUID charId, String emotionType, int candidateIndex) {
        Character character = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));

        List<Map<String, Object>> expressions = character.getExpressions();
        if (expressions == null || expressions.isEmpty()) {
            throw new BusinessException("NO_EXPRESSIONS", "尚无表情候选");
        }

        for (Map<String, Object> expr : expressions) {
            if (emotionType.equals(expr.get("type"))) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) expr.get("candidates");
                if (candidates == null || candidateIndex < 0 || candidateIndex >= candidates.size()) {
                    throw new BusinessException("INVALID_CANDIDATE", "表情候选索引无效: " + emotionType);
                }
                expr.put("selectedIndex", candidateIndex);
                break;
            }
        }

        boolean allSelected = expressions.stream().allMatch(e -> e.containsKey("selectedIndex"));
        if (allSelected) {
            character.setImageGenerationStatus("COMPLETED");
            storeDesignJson(projectId, character);
        }

        characterRepository.save(character);
        log.info("Character {}: selected expression {}[{}], status={}",
                character.getName(), emotionType, candidateIndex, character.getImageGenerationStatus());

        if (allSelected) {
            checkAllCharactersComplete(projectId);
        }
    }

    // ============================================================
    // Regeneration
    // ============================================================

    @Transactional
    public void regenerateThreeView(UUID projectId, UUID charId) {
        Character character = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));

        String referenceOssUrl = (String) character.getAppearance().get("portraitUrl");
        if (referenceOssUrl == null || referenceOssUrl.isBlank()) {
            throw new BusinessException("NO_PORTRAIT", "请先完成立绘抽卡");
        }

        character.setImageGenerationStatus("THREEVIEW_GENERATING");
        characterRepository.save(character);

        String appearanceDesc = buildAppearanceDescription(character);
        self().submitThreeViewTask(projectId, charId, character.getName(), appearanceDesc, referenceOssUrl);
    }

    @Transactional
    public void regenerateExpressions(UUID projectId, UUID charId) {
        Character character = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));

        String referenceOssUrl = (String) character.getAppearance().get("portraitUrl");
        if (referenceOssUrl == null || referenceOssUrl.isBlank()) {
            throw new BusinessException("NO_PORTRAIT", "请先完成立绘抽卡");
        }

        character.setExpressions(new ArrayList<>());
        character.setImageGenerationStatus("EXPRESSIONS_GENERATING");
        characterRepository.save(character);

        String appearanceDesc = buildAppearanceDescription(character);
        self().submitExpressionTasks(projectId, charId, character.getName(), appearanceDesc, referenceOssUrl);
    }

    // ============================================================
    // Status queries
    // ============================================================

    public Map<String, Object> getImageStatus(UUID charId) {
        Character character = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("imageGenerationStatus", character.getImageGenerationStatus());
        status.put("candidatePortraits", character.getCandidatePortraits());
        status.put("selectedPortraitIndex", character.getSelectedPortraitIndex());
        status.put("portraitUrl", character.getAppearance() != null ? character.getAppearance().get("portraitUrl") : null);
        status.put("threeViewUrl", character.getAppearance() != null ? character.getAppearance().get("threeViewUrl") : null);
        status.put("referenceImageUrl", character.getAppearance() != null ? character.getAppearance().get("referenceImageUrl") : null);
        status.put("expressions", character.getExpressions());
        return status;
    }

    public Map<String, Object> getThreeViewStatus(UUID charId) {
        Character character = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", character.getImageGenerationStatus());
        result.put("threeViewUrl", character.getAppearance() != null ? character.getAppearance().get("threeViewUrl") : null);
        result.put("threeViewLocalUrl", character.getAppearance() != null ? character.getAppearance().get("threeViewLocalUrl") : null);
        return result;
    }

    public Map<String, Object> getExpressionCandidates(UUID charId) {
        Character character = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", character.getImageGenerationStatus());
        result.put("expressions", character.getExpressions() != null ? character.getExpressions() : List.of());
        return result;
    }

    // ============================================================
    // Internal
    // ============================================================

    private void checkAllCharactersComplete(UUID projectId) {
        List<Character> allChars = characterRepository.findByProjectId(projectId);
        boolean allDone = allChars.stream().allMatch(c -> "COMPLETED".equals(c.getImageGenerationStatus()));
        if (!allDone) return;

        log.info("All {} character images complete for project {}, triggering next step", allChars.size(), projectId);

        eventPublisher.publishEvent(new WorkflowEvent.StepCompletedEvent(
                this, projectId, "CHARACTER_DESIGN",
                Map.of("characters", allChars.stream()
                        .map(c -> Map.of("id", c.getId().toString(), "name", c.getName()))
                        .toList()),
                0, null));

        workflowLogService.updateStatus(projectId, WorkflowStep.STORYBOARD_DESIGN,
                StepStatus.RUNNING, null, null, 0);

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project != null) progressService.refreshProgress(project);
    }

    private void storeDesignJson(UUID projectId, Character character) {
        try {
            Map<String, Object> designData = new LinkedHashMap<>();
            designData.put("name", character.getName());
            designData.put("portraitUrl", character.getAppearance().get("portraitUrl"));
            designData.put("threeViewUrl", character.getAppearance().get("threeViewUrl"));
            designData.put("referenceImageUrl", character.getAppearance().get("referenceImageUrl"));
            designData.put("expressions", character.getExpressions());
            imageStorageService.storeProjectText(projectId,
                    String.format("02-characters/%s/design.json", sanitizeForPath(character.getName())),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(designData));
        } catch (Exception e) {
            log.warn("Failed to store design.json for {}: {}", character.getName(), e.getMessage());
        }
    }

    private String buildAppearanceDescription(Character character) {
        StringBuilder sb = new StringBuilder();
        sb.append(character.getGender() != null ? character.getGender() : "").append("，");
        sb.append(character.getAgeRange() != null ? character.getAgeRange() : "").append("，");
        Map<String, Object> appearance = character.getAppearance();
        if (appearance != null) {
            if (appearance.containsKey("hairStyle")) sb.append(appearance.get("hairStyle")).append("发型，");
            if (appearance.containsKey("hairColor")) sb.append(appearance.get("hairColor")).append("发色，");
            if (appearance.containsKey("clothing")) sb.append("穿着").append(appearance.get("clothing")).append("，");
            if (appearance.containsKey("features")) sb.append(appearance.get("features"));
        }
        return sb.toString();
    }

    private String sanitizeForPath(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_");
    }
}
