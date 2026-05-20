package com.example.makemovie.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Speech-to-Text client.
 * Default: OpenAI Whisper API (or compatible)
 * Can also use local whisper CLI via subprocess.
 */
@Slf4j
@Component
public class SpeechToTextClient {

    private final RestClient restClient;
    private final String provider;

    public SpeechToTextClient(@Value("${speech-to-text.provider:whisper}") String provider,
                              @Value("${speech-to-text.api-key:}") String apiKey,
                              @Value("${speech-to-text.api-base:}") String apiBase,
                              RestClient.Builder builder) {
        this.provider = provider;
        if (apiBase != null && !apiBase.isBlank()) {
            this.restClient = builder
                    .baseUrl(apiBase)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build();
        } else {
            this.restClient = null;
        }
    }

    /**
     * Transcribe audio file to text.
     * Returns a simple text transcript.
     * Phase 5+: may return timestamped segments.
     */
    public String transcribe(String audioPath) {
        if (restClient == null) {
            log.warn("STT service not configured, returning empty transcript");
            return "";
        }

        try {
            log.info("Transcribing audio: {}", audioPath);

            // For a real implementation, this would use multipart file upload
            // Simplified: send a request with the audio path
            Map<String, Object> body = Map.of(
                    "audio_path", audioPath,
                    "language", "zh",
                    "response_format", "text"
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/audio/transcriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            if (response != null && response.containsKey("text")) {
                String text = (String) response.get("text");
                log.info("Transcription complete: {} chars", text.length());
                return text;
            }
            return "";
        } catch (Exception e) {
            log.error("STT failed: {}", e.getMessage());
            return "";
        }
    }
}
