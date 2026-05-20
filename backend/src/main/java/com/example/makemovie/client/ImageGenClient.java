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
 * 文生图客户端 — 通义万相 (non-blocking submit + poll).
 * API: https://help.aliyun.com/zh/model-studio/getting-started/models
 */
@Slf4j
@Component
public class ImageGenClient {

    private final RestClient restClient;
    private final String model;

    public ImageGenClient(@Value("${image-gen.api-key:}") String apiKey,
                          @Value("${image-gen.api-base:}") String apiBase,
                          @Value("${image-gen.model:wanx-v1}") String model,
                          RestClient.Builder builder) {
        this.model = model;
        if (apiBase != null && !apiBase.isBlank() && apiKey != null && !apiKey.isBlank()) {
            this.restClient = builder
                    .baseUrl(apiBase)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("X-DashScope-Async", "enable")
                    .build();
            log.info("ImageGen Client: model={}, base={}", model, apiBase);
        } else {
            this.restClient = null;
            log.info("ImageGen Client: disabled (no key/base configured)");
        }
    }

    public boolean isConfigured() {
        return restClient != null;
    }

    // ============================================================
    // Non-blocking: submit + separate poll
    // ============================================================

    /**
     * Submit an image generation task and return the taskId immediately (non-blocking).
     * The caller should save the taskId and use {@link #pollTask} to check completion.
     */
    public String submitTask(Map<String, Object> body) {
        if (restClient == null) {
            log.warn("ImageGen: not configured");
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> submitResp = restClient.post()
                    .uri("/api/v1/services/aigc/text2image/image-synthesis")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (submitResp == null || !submitResp.containsKey("output")) {
                log.warn("ImageGen: submit failed, response={}", submitResp);
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) submitResp.get("output");
            String taskId = (String) output.get("task_id");
            String taskStatus = (String) output.get("task_status");
            log.info("ImageGen: task submitted, id={}, status={}", taskId, taskStatus);
            return taskId;
        } catch (Exception e) {
            log.error("ImageGen: submit failed — {}", e.getMessage());
            return null;
        }
    }

    /**
     * Poll a single task once. Non-blocking — returns null if still processing.
     *
     * @return PollResult with status (COMPLETED/FAILED/PENDING) and resultUrl on success
     */
    public PollResult pollTask(String taskId) {
        if (taskId == null || restClient == null) {
            return PollResult.builder().status("FAILED").errorMessage("No taskId or client").build();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> statusResp = restClient.get()
                    .uri("/api/v1/tasks/" + taskId)
                    .retrieve()
                    .body(Map.class);

            if (statusResp == null || !statusResp.containsKey("output")) {
                return PollResult.builder().status("PENDING").build();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> output = (Map<String, Object>) statusResp.get("output");
            String status = (String) output.get("task_status");

            if ("SUCCEEDED".equals(status)) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> results =
                        (List<Map<String, String>>) output.get("results");
                if (results != null && !results.isEmpty()) {
                    String url = results.get(0).get("url");
                    log.info("ImageGen: task {} completed", taskId);
                    return PollResult.builder().status("COMPLETED").resultUrl(url).build();
                }
                return PollResult.builder().status("FAILED").errorMessage("No results in response").build();
            }

            if ("FAILED".equals(status)) {
                String msg = output.get("message") != null ? output.get("message").toString() : "Unknown";
                log.warn("ImageGen: task {} FAILED: {}", taskId, msg);
                return PollResult.builder().status("FAILED").errorMessage(msg).build();
            }

            return PollResult.builder().status("PENDING").build();
        } catch (Exception e) {
            log.warn("ImageGen: poll failed for {}: {}", taskId, e.getMessage());
            return PollResult.builder().status("PENDING").build();
        }
    }

    /**
     * Build a standard image gen request body.
     */
    public Map<String, Object> buildImageBody(String prompt, String size) {
        return Map.of(
                "model", model,
                "input", Map.of("prompt", prompt),
                "parameters", Map.of("size", size != null ? size : "1024*1024", "n", 1)
        );
    }

    /**
     * Build a body with reference image.
     */
    public Map<String, Object> buildRefImageBody(String prompt, String referenceUrl, String size) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", prompt);
        if (referenceUrl != null && !referenceUrl.isBlank()) {
            input.put("ref_img", referenceUrl);
        }
        return Map.of(
                "model", model,
                "input", input,
                "parameters", Map.of("size", size != null ? size : "1024*1024", "n", 1)
        );
    }

    // ============================================================
    // Prompt builders (stateless helpers)
    // ============================================================

    public String buildPortraitPrompt(String name, String appearanceDesc, String variation) {
        return String.format(
                "【角色立绘】\n角色：%s\n外观：%s\n姿态：%s\n要求：动漫风格，全身像，线条干净，色彩鲜艳，白色背景，高质量",
                name,
                appearanceDesc != null && !appearanceDesc.isBlank() ? appearanceDesc : "精致面容",
                variation);
    }

    public String buildThreeViewPrompt(String name, String appearanceDesc) {
        return String.format(
                "【角色三视图】\n角色：%s\n外观：%s\n布局：正面居中，右侧面在右边，背面在最右侧\n"
                        + "要求：全身像，展示完整服装设计，线条干净利落，白色背景，高质量角色参考图",
                name,
                appearanceDesc != null && !appearanceDesc.isBlank() ? appearanceDesc : "精致面容");
    }

    public String buildExpressionPrompt(String name, String expr, String appearanceDesc, String exprPose, String exprLabel) {
        return String.format(
                "【角色表情】\n角色：%s\n表情：%s\n外观：%s\n姿态：%s\n"
                        + "要求：保持与参考图一致的动漫角色外观，高质量",
                name, exprLabel, appearanceDesc, exprPose);
    }

    // ============================================================
    // Convenience builders
    // ============================================================

    public String generateSceneBackground(String description, String shotType, String timeOfDay, String track) {
        String prompt = String.format(
                "【场景背景】\n描述：%s\n镜头：%s\n光线：%s\n赛道：%s\n"
                        + "要求：动漫风格，不要出现人物角色，纯场景背景，画面丰富，细节精致，16:9构图，高质量",
                description, shotType != null ? shotType : "中景",
                timeOfDay != null ? timeOfDay : "白天",
                track != null ? track : "都市");
        return submitTask(buildImageBody(prompt, "1280*720"));
    }

    @Data
    @Builder
    public static class PollResult {
        private String status;      // COMPLETED, FAILED, PENDING
        private String resultUrl;
        private String errorMessage;
    }
}
