package com.example.makemovie.controller;

import com.example.makemovie.client.TtsClient;
import com.example.makemovie.entity.Composition;
import com.example.makemovie.entity.CompositionTask;
import com.example.makemovie.entity.WorkflowLog;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.repository.CompositionRepository;
import com.example.makemovie.repository.WorkflowLogRepository;
import com.example.makemovie.service.ClipGenerationService;
import com.example.makemovie.service.VideoComposerService;
import com.example.makemovie.service.VoiceGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}")
@RequiredArgsConstructor
public class CompositionController {

    private final VideoComposerService videoComposerService;
    private final VoiceGenerationService voiceGenerationService;
    private final ClipGenerationService clipGenerationService;
    private final CompositionRepository compositionRepository;
    private final TtsClient ttsClient;
    private final WorkflowLogRepository workflowLogRepository;
    private final ObjectMapper objectMapper;

    // ============================================================
    // Final Composition endpoints
    // ============================================================

    @PostMapping("/compositions")
    public ResponseEntity<Map<String, Object>> submitComposition(
            @PathVariable UUID projectId) {
        UUID taskId = videoComposerService.submitComposition(projectId);
        return ResponseEntity.ok(Map.of(
                "message", "合成任务已提交",
                "taskId", taskId,
                "projectId", projectId
        ));
    }

    @GetMapping("/compositions")
    public ResponseEntity<Map<String, Object>> getComposition(
            @PathVariable UUID projectId) {
        Composition comp = videoComposerService.getComposition(projectId);
        return ResponseEntity.ok(Map.of(
                "id", comp.getId(),
                "projectId", comp.getProjectId(),
                "videoUrl", comp.getVideoUrl() != null ? comp.getVideoUrl() : "",
                "coverUrl", comp.getCoverUrl() != null ? comp.getCoverUrl() : "",
                "bgmMaterialId", comp.getBgmMaterialId() != null ? comp.getBgmMaterialId().toString() : "",
                "status", comp.getStatus(),
                "progress", comp.getProgress(),
                "durationSec", comp.getDurationSec() != null ? comp.getDurationSec() : 0,
                "compositionType", comp.getCompositionType() != null ? comp.getCompositionType() : "LEGACY",
                "createdAt", comp.getCreatedAt()
        ));
    }

    @PatchMapping("/compositions/{compId}")
    public ResponseEntity<Map<String, Object>> updateComposition(
            @PathVariable UUID projectId,
            @PathVariable UUID compId,
            @RequestBody Map<String, Object> updates) {
        Composition comp = videoComposerService.getComposition(projectId);
        if (updates.containsKey("bgmMaterialId")) {
            Object bgmId = updates.get("bgmMaterialId");
            comp.setBgmMaterialId(bgmId != null && !bgmId.toString().isEmpty()
                    ? UUID.fromString(bgmId.toString()) : null);
            compositionRepository.save(comp);
        }
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/compositions/{compId}/progress")
    public ResponseEntity<Map<String, Object>> getProgress(
            @PathVariable UUID projectId,
            @PathVariable UUID compId) {
        CompositionTask task = videoComposerService.getTask(compId);
        return ResponseEntity.ok(Map.of(
                "taskId", task.getId(),
                "status", task.getStatus(),
                "progress", task.getProgress(),
                "currentFrame", task.getCurrentFrame() != null ? task.getCurrentFrame() : 0,
                "totalFrames", task.getTotalFrames() != null ? task.getTotalFrames() : 0,
                "errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "",
                "ffmpegLog", task.getFfmpegLog() != null
                        ? task.getFfmpegLog().substring(0,
                                Math.min(500, task.getFfmpegLog().length()))
                        : ""
        ));
    }

    @GetMapping("/compositions/{compId}/download")
    public ResponseEntity<InputStreamResource> downloadVideo(
            @PathVariable UUID projectId,
            @PathVariable UUID compId) {
        Composition comp = videoComposerService.getComposition(projectId);
        String videoUrl = comp.getVideoUrl();
        if (videoUrl == null || videoUrl.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            String localPathStr = videoComposerService.resolveToLocalPathStr(videoUrl);
            if (localPathStr == null) {
                return ResponseEntity.notFound().build();
            }
            Path localPath = Path.of(localPathStr);
            if (!Files.exists(localPath)) {
                return ResponseEntity.notFound().build();
            }

            InputStream stream = Files.newInputStream(localPath);
            String filename = "episode_" + projectId.toString().substring(0, 8) + ".mp4";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/mp4"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentLength(Files.size(localPath))
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.error("Download failed for comp {}: {}", compId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    // ============================================================
    // Voice Generation endpoints
    // ============================================================

    @PostMapping("/voice")
    public ResponseEntity<Map<String, Object>> generateVoice(
            @PathVariable UUID projectId) {
        voiceGenerationService.generateAllVoices(projectId);
        return ResponseEntity.ok(Map.of(
                "message", "配音生成完成",
                "projectId", projectId
        ));
    }

    @PostMapping("/voice/preview")
    public ResponseEntity<?> previewVoice(
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        String text = body.containsKey("text") ? (String) body.get("text") : "测试语音预览";
        String voice = body.containsKey("voice") ? (String) body.get("voice") : "zh-CN-XiaoxiaoNeural";
        double speed = body.containsKey("speed") ? ((Number) body.get("speed")).doubleValue() : 1.0;

        byte[] audioBytes = ttsClient.preview(text, voice, speed);
        if (audioBytes.length == 0) {
            return ResponseEntity.internalServerError().body(Map.of("error", "TTS合成失败"));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.mp3\"")
                .body(audioBytes);
    }

    @GetMapping("/voice/prompt")
    public ResponseEntity<Map<String, Object>> getVoicePrompt(
            @PathVariable UUID projectId) {
        Optional<WorkflowLog> wfLog = workflowLogRepository
                .findByProjectIdAndStep(projectId, WorkflowStep.VOICE_GENERATION);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("projectId", projectId.toString());
        if (wfLog.isPresent() && wfLog.get().getOutputData() != null) {
            result.put("prompt", wfLog.get().getPrompt() != null ? wfLog.get().getPrompt() : "");
            result.put("outputData", wfLog.get().getOutputData());
        } else {
            result.put("prompt", "");
            result.put("outputData", Map.of());
        }
        return ResponseEntity.ok(result);
    }

    // ============================================================
    // Clip Generation endpoints (frame-by-frame loop)
    // ============================================================

    @PostMapping("/clips/start")
    public ResponseEntity<Map<String, Object>> initClips(
            @PathVariable UUID projectId) {
        Map<String, Object> prereqs = clipGenerationService.checkPrerequisites(projectId);
        boolean ready = (boolean) prereqs.getOrDefault("ready", false);
        if (!ready) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "前置条件未满足，请先完成角色立绘生成",
                    "prerequisites", prereqs
            ));
        }
        clipGenerationService.initializeClipGeneration(projectId);
        return ResponseEntity.ok(Map.of(
                "message", "视频片段生成已初始化",
                "projectId", projectId
        ));
    }

    @GetMapping("/clips/prerequisites")
    public ResponseEntity<Map<String, Object>> getClipPrerequisites(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(clipGenerationService.checkPrerequisites(projectId));
    }

    @GetMapping("/clips/progress")
    public ResponseEntity<Map<String, Object>> getClipProgress(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(clipGenerationService.getProgress(projectId));
    }

    @GetMapping("/clips/current")
    public ResponseEntity<Map<String, Object>> getCurrentClip(
            @PathVariable UUID projectId) {
        Map<String, Object> progress = clipGenerationService.getProgress(projectId);
        return ResponseEntity.ok(progress);
    }

    @PutMapping("/clips/frames/{frameId}/prompt")
    public ResponseEntity<Map<String, Object>> updateFramePrompt(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId,
            @RequestBody Map<String, String> body) {
        String prompt = body.get("prompt");
        clipGenerationService.updateFramePrompt(frameId, prompt);
        return ResponseEntity.ok(Map.of(
                "message", "Prompt已更新",
                "frameId", frameId
        ));
    }

    @PostMapping("/clips/frames/{frameId}/generate")
    public ResponseEntity<Map<String, Object>> generateFrameClip(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId) {
        var task = clipGenerationService.generateFrameClip(projectId, frameId);
        return ResponseEntity.ok(Map.of(
                "message", "视频片段生成已触发",
                "frameId", frameId,
                "status", task.getStatus(),
                "videoUrl", task.getVideoUrl() != null ? task.getVideoUrl() : ""
        ));
    }

    @PostMapping("/clips/frames/{frameId}/approve")
    public ResponseEntity<Map<String, Object>> approveFrame(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId) {
        var task = clipGenerationService.approveFrame(projectId, frameId);
        return ResponseEntity.ok(Map.of(
                "message", "该帧已通过审核",
                "frameId", frameId,
                "status", task.getStatus()
        ));
    }

    @PostMapping("/clips/frames/{frameId}/skip")
    public ResponseEntity<Map<String, Object>> skipFrame(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId) {
        var task = clipGenerationService.skipFrame(projectId, frameId);
        return ResponseEntity.ok(Map.of(
                "message", "该帧已跳过",
                "frameId", frameId
        ));
    }

    @PostMapping("/clips/frames/{frameId}/retry")
    public ResponseEntity<Map<String, Object>> retryFrame(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId) {
        clipGenerationService.retryFrame(frameId);
        return ResponseEntity.ok(Map.of(
                "message", "该帧已重置为待生成",
                "frameId", frameId
        ));
    }

    @GetMapping("/clips/frames/{frameId}/detail")
    public ResponseEntity<Map<String, Object>> getFrameDetail(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId) {
        return ResponseEntity.ok(clipGenerationService.getFrameDetail(frameId));
    }
}
