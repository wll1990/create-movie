package com.example.makemovie.service;

import com.example.makemovie.client.VideoGenClient;
import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.ClipTask;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Storyboard;
import com.example.makemovie.entity.StoryboardFrame;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.event.WorkflowEvent;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Manages the frame-by-frame AI video generation loop.
 *
 * Workflow:
 * 1. Initialize: create ClipTask entries for all frames (status = PENDING)
 * 2. For each frame in order:
 *    a. Set frame status to PROMPT_READY (prompt already generated in StoryboardService)
 *    b. User reviews/edits prompt (via API)
 *    c. User triggers generation: status → GENERATING, call VideoGenClient
 *    d. On completion: status → COMPLETED, user reviews video
 *    e. User approves: status → APPROVED, move to next frame
 *
 * After all frames are APPROVED, auto-triggers FINAL_COMPOSITION.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClipGenerationService {

    private final VideoGenClient videoGenClient;
    private final ClipTaskRepository clipTaskRepository;
    private final StoryboardFrameRepository frameRepository;
    private final StoryboardRepository storyboardRepository;
    private final CharacterRepository characterRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowLogService workflowLogService;
    private final ProgressService progressService;
    private final ImageStorageService imageStorageService;
    private final VideoGenPromptBuilder promptBuilder;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${video-gen.max-parallel:1}")
    private int maxParallel;

    /**
     * Check prerequisites for clip generation.
     * Returns a map with ready status and details about what's missing.
     */
    public Map<String, Object> checkPrerequisites(UUID projectId) {
        Map<String, Object> result = new LinkedHashMap<>();

        var storyboardOpt = storyboardRepository.findByProjectId(projectId);
        if (storyboardOpt.isEmpty()) {
            result.put("ready", false);
            result.put("storyboardReady", false);
            result.put("message", "请先生成分镜");
            return result;
        }
        result.put("storyboardReady", true);

        List<Character> characters = characterRepository.findByProjectId(projectId);
        List<Map<String, Object>> charStatus = new ArrayList<>();
        boolean allCharsReady = true;

        for (Character c : characters) {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("name", c.getName());
            cs.put("id", c.getId().toString());

            Map<String, Object> app = c.getAppearance();
            boolean hasRef = app != null && app.containsKey("referenceImageUrl")
                    && app.get("referenceImageUrl") != null
                    && !app.get("referenceImageUrl").toString().isBlank();
            boolean hasPortrait = app != null && app.containsKey("portraitUrl")
                    && app.get("portraitUrl") != null
                    && !app.get("portraitUrl").toString().isBlank();
            boolean hasExpressions = c.getExpressions() != null && !c.getExpressions().isEmpty()
                    && c.getExpressions().stream().anyMatch(e -> e.containsKey("ossUrl") || e.containsKey("imageUrl"));

            cs.put("hasReferenceImage", hasRef);
            cs.put("hasPortrait", hasPortrait);
            cs.put("hasExpressions", hasExpressions);
            cs.put("imageStatus", c.getImageGenerationStatus());
            charStatus.add(cs);

            if (!hasRef) allCharsReady = false;
        }

        result.put("charactersReady", allCharsReady);
        result.put("characters", charStatus);
        result.put("ready", allCharsReady);
        if (!allCharsReady) {
            result.put("message", "请先生成角色立绘和三视图（在角色卡片中完成抽卡选择）");
        } else {
            result.put("message", "所有角色参考图已就绪");
        }
        return result;
    }

    /**
     * Initialize clip generation for this project.
     * Creates a ClipTask for every storyboard frame with status PENDING.
     * Sets the first frame to PROMPT_READY.
     */
    @Transactional
    public void initializeClipGeneration(UUID projectId) {
        var storyboard = storyboardRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("STORYBOARD_NOT_FOUND", "请先生成分镜"));

        List<StoryboardFrame> frames = frameRepository
                .findByStoryboardIdOrderByFrameNumber(storyboard.getId());

        // Delete previous clip tasks if re-initializing
        List<ClipTask> existing = clipTaskRepository.findByProjectIdOrderByFrameNumber(projectId);
        clipTaskRepository.deleteAll(existing);

        for (StoryboardFrame frame : frames) {
            ClipTask task = ClipTask.builder()
                    .storyboardFrameId(frame.getId())
                    .projectId(projectId)
                    .frameNumber(frame.getFrameNumber())
                    .status(frame.getFrameNumber() == 1 ? "PROMPT_READY" : "PENDING")
                    .clipPrompt(frame.getClipPrompt())
                    .referenceImageUrl(resolveReferenceUrl(frame))
                    .expressionImageUrl(resolveExpressionUrl(frame))
                    .backgroundImageUrl(frame.getBgImageUrl())
                    .build();
            clipTaskRepository.save(task);
        }

        log.info("Clip generation initialized: projectId={}, frames={}", projectId, frames.size());
    }

    /**
     * Generate a video clip for a specific frame.
     * Called by user action (frontend trigger).
     */
    @Transactional
    public ClipTask generateFrameClip(UUID projectId, UUID frameId) {
        ClipTask task = clipTaskRepository.findByStoryboardFrameId(frameId)
                .orElseThrow(() -> new BusinessException("CLIP_TASK_NOT_FOUND", "未找到该帧的生成任务"));

        StoryboardFrame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new BusinessException("FRAME_NOT_FOUND", "分镜帧不存在"));

        // Check prerequisites — need at least a reference image
        String referenceUrl = task.getReferenceImageUrl();
        String bgUrl = task.getBackgroundImageUrl();
        if ((referenceUrl == null || referenceUrl.isBlank()) && (bgUrl == null || bgUrl.isBlank())) {
            task.setStatus("FAILED");
            task.setErrorMessage("NO_REFERENCE_IMAGE: 请先生成角色立绘和三视图（在角色卡片中完成抽卡选择）");
            clipTaskRepository.save(task);
            frame.setClipStatus("FAILED");
            frameRepository.save(frame);
            return task;
        }

        task.setStatus("GENERATING");
        task.setStartedAt(LocalDateTime.now());
        clipTaskRepository.save(task);

        frame.setClipStatus("GENERATING");
        frameRepository.save(frame);

        // Use the prompt from the task (user may have edited it)
        String prompt = task.getClipPrompt() != null ? task.getClipPrompt() : frame.getClipPrompt();

        try {
            double duration = frame.getDurationSec() != null ? frame.getDurationSec() : 3.0;

            VideoGenClient.VideoGenerationResult result = videoGenClient.generateSync(
                    prompt,
                    task.getReferenceImageUrl(),
                    task.getExpressionImageUrl(),
                    task.getBackgroundImageUrl(),
                    duration);

            if ("COMPLETED".equals(result.getStatus()) && result.getVideoUrl() != null) {
                // Download and store video clip
                byte[] videoBytes = downloadVideo(result.getVideoUrl());
                String clipPath = String.format("05-video-clips/clips/frame_%03d.mp4",
                        frame.getFrameNumber());
                String persistentUrl = imageStorageService.storeProjectFile(
                        projectId, clipPath, videoBytes, "video/mp4");

                task.setStatus("COMPLETED");
                task.setVideoUrl(persistentUrl);
                task.setCompletedAt(LocalDateTime.now());

                frame.setClipVideoUrl(persistentUrl);
                frame.setClipStatus("COMPLETED");
            } else {
                task.setStatus("FAILED");
                task.setErrorMessage(result.getErrorMessage());
                frame.setClipStatus("FAILED");
            }
        } catch (Exception e) {
            log.error("Clip generation failed for frame {}: {}", frame.getFrameNumber(), e.getMessage());
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            frame.setClipStatus("FAILED");
        }

        clipTaskRepository.save(task);
        frameRepository.save(frame);

        return task;
    }

    /**
     * User approves the generated clip, advancing to the next frame.
     */
    @Transactional
    public ClipTask approveFrame(UUID projectId, UUID frameId) {
        ClipTask task = clipTaskRepository.findByStoryboardFrameId(frameId)
                .orElseThrow(() -> new BusinessException("CLIP_TASK_NOT_FOUND", "未找到该帧的生成任务"));

        if (!"COMPLETED".equals(task.getStatus())) {
            throw new BusinessException("CLIP_NOT_READY", "该帧视频尚未生成完成，无法审批");
        }

        task.setStatus("APPROVED");
        task.setApprovedAt(LocalDateTime.now());
        clipTaskRepository.save(task);

        StoryboardFrame frame = frameRepository.findById(frameId).orElseThrow();
        frame.setClipStatus("APPROVED");
        frameRepository.save(frame);

        // Advance to the next frame: set it to PROMPT_READY
        advanceToNextFrame(projectId, task.getFrameNumber());

        // Check if ALL frames are approved
        checkAllFramesComplete(projectId);

        return task;
    }

    /**
     * Skip a frame — marks it as SKIPPED and advances.
     */
    @Transactional
    public ClipTask skipFrame(UUID projectId, UUID frameId) {
        ClipTask task = clipTaskRepository.findByStoryboardFrameId(frameId)
                .orElseThrow(() -> new BusinessException("CLIP_TASK_NOT_FOUND", "未找到该帧的生成任务"));

        task.setStatus("SKIPPED");
        clipTaskRepository.save(task);

        StoryboardFrame frame = frameRepository.findById(frameId).orElseThrow();
        frame.setClipStatus("SKIPPED");
        frameRepository.save(frame);

        advanceToNextFrame(projectId, task.getFrameNumber());
        checkAllFramesComplete(projectId);

        return task;
    }

    /**
     * Update a frame's prompt (user edit before generation).
     */
    @Transactional
    public void updateFramePrompt(UUID frameId, String newPrompt) {
        ClipTask task = clipTaskRepository.findByStoryboardFrameId(frameId)
                .orElseThrow(() -> new BusinessException("CLIP_TASK_NOT_FOUND", "未找到该帧的生成任务"));

        task.setClipPrompt(newPrompt);
        clipTaskRepository.save(task);

        StoryboardFrame frame = frameRepository.findById(frameId).orElseThrow();
        frame.setClipPrompt(newPrompt);
        frameRepository.save(frame);

        // Also update the prompt file
        String promptPath = String.format("03-storyboard/prompts/frame_%03d.txt",
                frame.getFrameNumber());
        imageStorageService.storeProjectText(task.getProjectId(), promptPath, newPrompt);
    }

    /**
     * Retry a failed frame — reset to PROMPT_READY.
     */
    @Transactional
    public void retryFrame(UUID frameId) {
        ClipTask task = clipTaskRepository.findByStoryboardFrameId(frameId)
                .orElseThrow(() -> new BusinessException("CLIP_TASK_NOT_FOUND", "未找到该帧的生成任务"));

        task.setStatus("PROMPT_READY");
        task.setErrorMessage(null);
        clipTaskRepository.save(task);

        StoryboardFrame frame = frameRepository.findById(frameId).orElseThrow();
        frame.setClipStatus("PROMPT_READY");
        frame.setClipRetryCount(frame.getClipRetryCount() != null
                ? frame.getClipRetryCount() + 1 : 1);
        frameRepository.save(frame);
    }

    /**
     * Get clip generation progress for the project.
     */
    public Map<String, Object> getProgress(UUID projectId) {
        List<ClipTask> tasks = clipTaskRepository.findByProjectIdOrderByFrameNumber(projectId);
        long approved = tasks.stream().filter(t -> "APPROVED".equals(t.getStatus())).count();
        long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long failed = tasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count();
        long skipped = tasks.stream().filter(t -> "SKIPPED".equals(t.getStatus())).count();

        ClipTask current = tasks.stream()
                .filter(t -> "PROMPT_READY".equals(t.getStatus())
                        || "GENERATING".equals(t.getStatus()))
                .findFirst()
                .orElse(null);

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("totalFrames", tasks.size());
        progress.put("approvedFrames", approved);
        progress.put("completedFrames", completed);
        progress.put("failedFrames", failed);
        progress.put("skippedFrames", skipped);
        progress.put("currentFrame", current != null ? current.getFrameNumber() : tasks.size());

        List<Map<String, Object>> frameList = new ArrayList<>();
        for (ClipTask t : tasks) {
            Map<String, Object> frameInfo = new LinkedHashMap<>();
            frameInfo.put("frameId", t.getStoryboardFrameId().toString());
            frameInfo.put("frameNumber", t.getFrameNumber());
            frameInfo.put("status", t.getStatus());
            frameInfo.put("videoUrl", t.getVideoUrl() != null ? t.getVideoUrl() : "");
            frameInfo.put("referenceImageUrl", t.getReferenceImageUrl() != null ? t.getReferenceImageUrl() : "");
            frameInfo.put("expressionImageUrl", t.getExpressionImageUrl() != null ? t.getExpressionImageUrl() : "");
            frameInfo.put("backgroundImageUrl", t.getBackgroundImageUrl() != null ? t.getBackgroundImageUrl() : "");
            frameInfo.put("clipPrompt", t.getClipPrompt() != null ? t.getClipPrompt() : "");
            frameList.add(frameInfo);
        }
        progress.put("frames", frameList);

        return progress;
    }

    /**
     * Get rich detail for a specific frame, including all reference images and metadata.
     */
    public Map<String, Object> getFrameDetail(UUID frameId) {
        ClipTask task = clipTaskRepository.findByStoryboardFrameId(frameId)
                .orElseThrow(() -> new BusinessException("CLIP_TASK_NOT_FOUND", "未找到该帧的生成任务"));

        StoryboardFrame frame = frameRepository.findById(frameId)
                .orElseThrow(() -> new BusinessException("FRAME_NOT_FOUND", "分镜帧不存在"));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("frameId", frame.getId().toString());
        detail.put("frameNumber", frame.getFrameNumber());
        detail.put("status", task.getStatus());
        detail.put("clipPrompt", task.getClipPrompt() != null ? task.getClipPrompt() : "");
        detail.put("clipVideoUrl", task.getVideoUrl() != null ? task.getVideoUrl() :
                (frame.getClipVideoUrl() != null ? frame.getClipVideoUrl() : ""));
        detail.put("referenceImageUrl", task.getReferenceImageUrl() != null ? task.getReferenceImageUrl() : "");
        detail.put("expressionImageUrl", task.getExpressionImageUrl() != null ? task.getExpressionImageUrl() : "");
        detail.put("backgroundImageUrl", task.getBackgroundImageUrl() != null ? task.getBackgroundImageUrl() : "");
        detail.put("shotType", frame.getShotType());
        detail.put("cameraAngle", frame.getCameraAngle());
        detail.put("subtitleText", frame.getSubtitleText() != null ? frame.getSubtitleText() : "");
        detail.put("durationSec", frame.getDurationSec());
        detail.put("bgDescription", frame.getBgDescription() != null ? frame.getBgDescription() : "");
        detail.put("characters", frame.getCharacters());
        detail.put("clipRetryCount", frame.getClipRetryCount() != null ? frame.getClipRetryCount() : 0);
        detail.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "");
        detail.put("modelParams", task.getModelParams());

        return detail;
    }

    private void advanceToNextFrame(UUID projectId, int currentFrameNumber) {
        List<ClipTask> tasks = clipTaskRepository.findByProjectIdOrderByFrameNumber(projectId);
        for (ClipTask t : tasks) {
            if (t.getFrameNumber() == currentFrameNumber + 1
                    && "PENDING".equals(t.getStatus())) {
                t.setStatus("PROMPT_READY");
                clipTaskRepository.save(t);

                StoryboardFrame frame = frameRepository.findById(t.getStoryboardFrameId())
                        .orElse(null);
                if (frame != null) {
                    frame.setClipStatus("PROMPT_READY");
                    frameRepository.save(frame);
                }
                break;
            }
        }
    }

    private void checkAllFramesComplete(UUID projectId) {
        List<ClipTask> tasks = clipTaskRepository.findByProjectIdOrderByFrameNumber(projectId);
        boolean allDone = tasks.stream()
                .allMatch(t -> "APPROVED".equals(t.getStatus())
                        || "SKIPPED".equals(t.getStatus()));

        if (allDone) {
            eventPublisher.publishEvent(new WorkflowEvent.StepCompletedEvent(
                    this, projectId, "CLIP_GENERATION",
                    Map.of("totalFrames", tasks.size(), "approved",
                            tasks.stream().filter(t -> "APPROVED".equals(t.getStatus())).count()),
                    0, null));

            // Auto-trigger next step: FINAL_COMPOSITION
            Project project = projectRepository.findById(projectId).orElseThrow();
            workflowLogService.updateStatus(projectId, WorkflowStep.FINAL_COMPOSITION,
                    StepStatus.RUNNING, null, null, 0);
            progressService.refreshProgress(project);

            log.info("All clips complete for project {}, triggering final composition", projectId);
        }
    }

    private String resolveReferenceUrl(StoryboardFrame frame) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frameChars =
                (List<Map<String, Object>>) (Object) frame.getCharacters();
        if (frameChars == null || frameChars.isEmpty()) return null;

        String charName = (String) frameChars.get(0).getOrDefault("characterName", "");
        if (charName.isBlank()) return null;

        UUID projectId = frameRepository.findById(frame.getId())
                .map(f -> storyboardRepository.findById(f.getStoryboardId())
                        .map(s -> s.getProjectId()).orElse(null))
                .orElse(null);
        if (projectId == null) return null;

        List<Character> characters = characterRepository.findByProjectId(projectId);
        return characters.stream()
                .filter(c -> c.getName().equals(charName))
                .findFirst()
                .map(c -> {
                    Map<String, Object> app = c.getAppearance();
                    return app != null ? (String) app.get("referenceImageUrl") : null;
                })
                .orElse(null);
    }

    private String resolveExpressionUrl(StoryboardFrame frame) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> frameChars =
                (List<Map<String, Object>>) (Object) frame.getCharacters();
        if (frameChars == null || frameChars.isEmpty()) return null;

        String charName = (String) frameChars.get(0).getOrDefault("characterName", "");
        String expression = (String) frameChars.get(0).getOrDefault("expression", "neutral");

        UUID storyboardId = frame.getStoryboardId();
        UUID projectId = storyboardRepository.findById(storyboardId)
                .map(Storyboard::getProjectId).orElse(null);
        if (projectId == null) return null;

        List<Character> characters = characterRepository.findByProjectId(projectId);
        return characters.stream()
                .filter(c -> c.getName().equals(charName))
                .findFirst()
                .flatMap(c -> c.getExpressions().stream()
                        .filter(e -> expression.equals(e.get("type")))
                        .findFirst()
                        .map(e -> (String) e.get("imageUrl")))
                .orElse(null);
    }

    private byte[] downloadVideo(String url) {
        if (url == null) return new byte[0];
        try (java.io.InputStream is = java.net.URI.create(url).toURL().openStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to download video from {}: {}", url, e.getMessage());
            return new byte[0];
        }
    }
}
