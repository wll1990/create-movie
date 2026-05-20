package com.example.makemovie.client;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图生视频客户端 — 通义万相 Wan2.7
 * API: https://help.aliyun.com/zh/model-studio/wan-video-gen
 * 异步模式: 提交任务 → 轮询结果 → 获取视频URL
 *
 * 要求参考图片是公网可访问的 HTTP/HTTPS URL，
 * 因此需要配合 OssService 将图片上传到阿里云 OSS。
 */
@Slf4j
@Component
public class VideoGenClient {

    private final RestClient restClient;
    private final String model;
    private final int maxPollAttempts;
    private final long pollIntervalMs;

    public VideoGenClient(@Value("${video-gen.api-key:}") String apiKey,
                          @Value("${video-gen.api-base:https://dashscope.aliyuncs.com}") String apiBase,
                          @Value("${video-gen.model:wan2.7-i2v}") String model,
                          @Value("${video-gen.max-poll-attempts:60}") int maxPollAttempts,
                          @Value("${video-gen.poll-interval-ms:5000}") long pollIntervalMs,
                          RestClient.Builder builder) {
        this.model = model;
        this.maxPollAttempts = maxPollAttempts;
        this.pollIntervalMs = pollIntervalMs;
        if (apiKey != null && !apiKey.isBlank()) {
            this.restClient = builder
                    .baseUrl(apiBase)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("X-DashScope-Async", "enable")
                    .build();
            log.info("VideoGen Client: model={}, base={}", model, apiBase);
        } else {
            this.restClient = null;
            log.info("VideoGen Client: disabled (no api-key configured)");
        }
    }

    /**
     * Submit a video generation task to Wan2.7.
     *
     * @param prompt             Text description of the desired video motion
     * @param referenceImageUrl  Character reference image (threeview) for consistency
     * @param expressionImageUrl Expression reference image
     * @param backgroundImageUrl Background / scene image (preferred as first_frame)
     * @param durationSec        Desired duration in seconds (Wan2.7: 2-5s)
     * @return Task ID for polling, or null on failure
     */
    public String submitTask(String prompt,
                             String referenceImageUrl,
                             String expressionImageUrl,
                             String backgroundImageUrl,
                             double durationSec) {
        if (restClient == null) {
            log.warn("VideoGen: not configured, skipping");
            return null;
        }

        // first_frame_image priority: background > reference > expression
        String firstFrameImage = backgroundImageUrl;
        if (firstFrameImage == null || firstFrameImage.isBlank()) {
            firstFrameImage = referenceImageUrl;
        }
        if (firstFrameImage == null || firstFrameImage.isBlank()) {
            firstFrameImage = expressionImageUrl;
        }
        if (firstFrameImage == null || firstFrameImage.isBlank()) {
            log.warn("VideoGen: no reference image available, cannot submit task");
            return null;
        }

        // Wan2.7 supports 2-5 seconds, default to 5
        int duration = (int) Math.max(2, Math.min(5, Math.ceil(durationSec)));

        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("first_frame_image", firstFrameImage);
            input.put("prompt", prompt != null ? prompt : "人物自然运动，画面流畅");

            Map<String, Object> body = Map.of(
                    "model", model,
                    "input", input,
                    "parameters", Map.of(
                            "duration", duration,
                            "resolution", "720p"
                    )
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> submitResp = restClient.post()
                    .uri("/api/v1/services/aigc/video-generation/video-synthesis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (submitResp == null || !submitResp.containsKey("output")) {
                log.warn("VideoGen: submit failed, response={}", submitResp);
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) submitResp.get("output");
            String taskId = (String) output.get("task_id");
            String taskStatus = (String) output.get("task_status");

            log.info("VideoGen: task submitted, id={}, status={}, duration={}s", taskId, taskStatus, duration);
            return taskId;
        } catch (Exception e) {
            log.error("VideoGen: submit task failed — {}", e.getMessage());
            throw new RuntimeException("视频生成任务提交失败: " + e.getMessage());
        }
    }

    /**
     * Poll task status until completion or failure.
     */
    public VideoGenerationResult pollTask(String taskId) {
        if (taskId == null) {
            return VideoGenerationResult.builder()
                    .status("FAILED")
                    .errorMessage("任务ID为空")
                    .build();
        }

        for (int i = 0; i < maxPollAttempts; i++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> statusResp = restClient.get()
                        .uri("/api/v1/tasks/" + taskId)
                        .retrieve()
                        .body(Map.class);

                if (statusResp == null) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> output = (Map<String, Object>) statusResp.get("output");
                if (output == null) continue;

                String status = (String) output.get("task_status");

                if ("SUCCEEDED".equals(status)) {
                    String videoUrl = extractVideoUrl(output);
                    log.info("VideoGen: complete, url={}...",
                            videoUrl != null ? videoUrl.substring(0, Math.min(60, videoUrl.length())) : "null");
                    return VideoGenerationResult.builder()
                            .status("COMPLETED")
                            .videoUrl(videoUrl)
                            .build();
                }

                if ("FAILED".equals(status)) {
                    String error = (String) output.getOrDefault("message", "Unknown error");
                    log.warn("VideoGen: FAILED, id={}, msg={}", taskId, error);
                    return VideoGenerationResult.builder()
                            .status("FAILED")
                            .errorMessage(error)
                            .build();
                }

                // Still processing — wait and retry
                Thread.sleep(pollIntervalMs);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return VideoGenerationResult.builder()
                        .status("FAILED")
                        .errorMessage("轮询被中断")
                        .build();
            } catch (Exception e) {
                log.warn("Poll attempt {} failed: {}", i + 1, e.getMessage());
            }
        }

        return VideoGenerationResult.builder()
                .status("TIMEOUT")
                .errorMessage("视频生成超时，已轮询" + maxPollAttempts + "次")
                .build();
    }

    /**
     * Submit and wait for completion (synchronous convenience method).
     */
    public VideoGenerationResult generateSync(String prompt,
                                               String referenceImageUrl,
                                               String expressionImageUrl,
                                               String backgroundImageUrl,
                                               double durationSec) {
        String taskId = submitTask(prompt, referenceImageUrl,
                expressionImageUrl, backgroundImageUrl, durationSec);
        if (taskId == null) {
            return VideoGenerationResult.builder()
                    .status("FAILED")
                    .errorMessage("提交任务失败")
                    .build();
        }
        return pollTask(taskId);
    }

    @SuppressWarnings("unchecked")
    private String extractVideoUrl(Map<String, Object> output) {
        // DashScope Wan2.7 format: output.results[0].url
        Object results = output.get("results");
        if (results instanceof List && !((List<?>) results).isEmpty()) {
            Object first = ((List<?>) results).get(0);
            if (first instanceof Map) {
                return (String) ((Map<String, Object>) first).get("url");
            }
        }
        // Fallback: output.video_url
        if (output.containsKey("video_url")) {
            return (String) output.get("video_url");
        }
        return null;
    }

    @Data
    @Builder
    public static class VideoGenerationResult {
        private String status;
        private String videoUrl;
        private String errorMessage;
    }
}
