package com.example.makemovie.client;

import com.example.makemovie.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * LLM client with dual-model support:
 * - Text model: for script, character, storyboard, copywriting (cheap & fast)
 * - Vision model: for video gene analysis (can "see" keyframe images)
 *
 * Both use OpenAI-compatible chat/completions API.
 */
@Slf4j
@Component
public class LlmClient {

    private final RestClient restClient;
    private final RestClient visionRestClient;
    private final String model;
    private final String visionModel;
    private final ObjectMapper objectMapper;

    public LlmClient(@Value("${llm.api-key}") String apiKey,
                     @Value("${llm.api-base}") String apiBase,
                     @Value("${llm.model}") String model,
                     @Value("${llm.vision-api-base:}") String visionApiBase,
                     @Value("${llm.vision-api-key:}") String visionApiKey,
                     @Value("${llm.vision-model:}") String visionModel,
                     RestClient.Builder builder,
                     ObjectMapper objectMapper) {
        this.model = model;
        this.visionModel = !visionModel.isBlank() ? visionModel : model;
        this.objectMapper = objectMapper;

        // Configure long timeouts for large prompts (storyboard etc.)
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(30_000);   // 30s
        requestFactory.setReadTimeout(180_000);     // 3 min

        this.restClient = builder
                .baseUrl(apiBase)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .requestFactory(requestFactory)
                .build();

        // Only enable vision client if BOTH base URL and key are configured
        if (!visionApiBase.isBlank() && !visionApiKey.isBlank()) {
            var visionRequestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            visionRequestFactory.setConnectTimeout(30_000);
            visionRequestFactory.setReadTimeout(180_000);
            this.visionRestClient = builder
                    .baseUrl(visionApiBase)
                    .defaultHeader("Authorization", "Bearer " + visionApiKey)
                    .requestFactory(visionRequestFactory)
                    .build();
            log.info("LLM Client: text-model={} | vision-model={} (separate provider)", model, visionModel);
        } else {
            this.visionRestClient = null;
            log.info("LLM Client: text-model={} | vision: disabled (no separate key)", model);
        }
    }

    // ============================================================
    // Text generation (script, characters, storyboard, copywriting)
    // ============================================================

    @CircuitBreaker(name = "llm", fallbackMethod = "llmFallback")
    @Retry(name = "llm")
    public String generate(String prompt) {
        return callLlm(restClient, model, prompt, null);
    }

    // ============================================================
    // Vision generation (video gene analysis)
    // ============================================================

    /**
     * Analyze images with a vision-capable model.
     *
     * @param prompt     Analysis prompt
     * @param imagePaths Paths to image files (keyframes). Max 10 to avoid token explosion.
     * @return LLM response text
     */
    @CircuitBreaker(name = "llm", fallbackMethod = "visionFallback")
    @Retry(name = "llm")
    public String generateWithImages(String prompt, List<String> imagePaths) {
        RestClient client = visionRestClient != null ? visionRestClient : restClient;
        String modelName = visionRestClient != null ? visionModel : model;

        int maxImages = Math.min(imagePaths.size(), 10);
        List<String> images = imagePaths.subList(0, maxImages);

        log.debug("Calling vision LLM: model={}, promptLen={}, images={}",
                modelName, prompt.length(), images.size());

        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", prompt));

        for (String imagePath : images) {
            try {
                String base64 = encodeImageToBase64(imagePath);
                contentParts.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:image/jpeg;base64," + base64)
                ));
            } catch (IOException e) {
                log.warn("Failed to encode image: {} — {}", imagePath, e.getMessage());
            }
        }

        return callLlmMultimodal(client, modelName, contentParts);
    }

    // ============================================================
    // Fallback handlers
    // ============================================================

    public String llmFallback(String prompt, Exception e) {
        log.error("LLM call failed (text), circuit open", e);
        throw new BusinessException("LLM_SERVICE_UNAVAILABLE",
                "LLM服务不可用: " + e.getMessage());
    }

    public String visionFallback(String prompt, List<String> images, Exception e) {
        log.error("Vision LLM failed, falling back to text-only analysis", e);
        // Fallback: analyze without images
        return generate(prompt);
    }

    // ============================================================
    // Internal: call API
    // ============================================================

    private String callLlm(RestClient client, String modelName,
                            String prompt, List<Map<String, Object>> contentParts) {
        long startTime = System.currentTimeMillis();

        Object messages;
        if (contentParts != null) {
            messages = List.of(
                    Map.of("role", "system", "content",
                            "你是一个专业的短视频创作助手，请严格按照要求的JSON格式输出。"),
                    Map.of("role", "user", "content", contentParts)
            );
        } else {
            messages = List.of(
                    Map.of("role", "system", "content",
                            "你是一个专业的短视频创作助手，请严格按照要求的JSON格式输出。"),
                    Map.of("role", "user", "content", prompt)
            );
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("messages", messages);
        body.put("max_tokens", 4096);
        body.put("temperature", modelName.contains("vision") || modelName.contains("vl") ? 0.4 : 0.8);

        var responseEntity = client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((req, resp) -> {
                    byte[] bytes = resp.getBody().readAllBytes();
                    String body2 = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    return new org.springframework.http.ResponseEntity<>(
                            body2, resp.getStatusCode());
                });

        long elapsed = System.currentTimeMillis() - startTime;
        int statusCode = responseEntity.getStatusCode().value();
        log.debug("LLM response in {}ms, status={}", elapsed, statusCode);

        String responseBody = responseEntity.getBody();
        if (responseBody == null || responseBody.isBlank()) {
            throw new BusinessException("LLM_EMPTY_RESPONSE", "LLM返回为空");
        }

        if (!responseEntity.getStatusCode().is2xxSuccessful()) {
            log.error("LLM API error ({}): {}", statusCode,
                    responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody);
            throw new BusinessException("LLM_API_ERROR",
                    "LLM API返回错误: " + statusCode);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> response;
        try {
            response = objectMapper.readValue(responseBody, Map.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException("LLM_PARSE_ERROR", "LLM响应JSON解析失败: " + e.getMessage());
        }

        if (!response.containsKey("choices")) {
            log.warn("LLM response missing 'choices': {}", responseBody.substring(0, Math.min(200, responseBody.length())));
            throw new BusinessException("LLM_EMPTY_RESPONSE", "LLM返回格式异常");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new BusinessException("LLM_EMPTY_RESPONSE", "LLM返回为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");
        return extractJson(content);
    }

    private String callLlmMultimodal(RestClient client, String modelName,
                                      List<Map<String, Object>> contentParts) {
        return callLlm(client, modelName, null, contentParts);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private String encodeImageToBase64(String imagePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(imagePath));
        return Base64.getEncoder().encodeToString(bytes);
    }

    private String extractJson(String content) {
        content = content.trim();
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }
        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }
        return content.trim();
    }
}
