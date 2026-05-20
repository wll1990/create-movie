package com.example.makemovie.config;

import com.example.makemovie.entity.Material;
import com.example.makemovie.enums.MaterialType;
import com.example.makemovie.repository.MaterialRepository;
import com.example.makemovie.service.ImageStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Auto-registers built-in BGM tracks from resources/bgm/ into the Material table.
 * Runs once on application startup, skips if BGM materials already exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BgmInitializer {

    private final MaterialRepository materialRepository;
    private final ImageStorageService imageStorageService;
    private final ObjectMapper objectMapper;

    private static final UUID SYSTEM_PROJECT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @EventListener(ApplicationReadyEvent.class)
    public void initializeBgm() {
        // Skip if BGM materials already registered
        List<Material> existing = materialRepository.findByCategory("BGM");
        if (!existing.isEmpty()) {
            log.info("BGM: {} tracks already registered, skipping initialization", existing.size());
            return;
        }

        log.info("BGM: initializing built-in tracks...");
        try {
            ClassPathResource metadataRes = new ClassPathResource("bgm/metadata.json");
            if (!metadataRes.exists()) {
                log.warn("BGM: metadata.json not found, skipping initialization");
                return;
            }

            Map<String, Object> metadata;
            try (InputStream is = metadataRes.getInputStream()) {
                metadata = objectMapper.readValue(is, new TypeReference<>() {});
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tracks = (List<Map<String, Object>>) metadata.get("tracks");
            if (tracks == null || tracks.isEmpty()) {
                log.warn("BGM: no tracks defined in metadata.json");
                return;
            }

            int count = 0;
            for (Map<String, Object> track : tracks) {
                try {
                    String name = (String) track.get("name");
                    String fileName = (String) track.get("fileName");

                    ClassPathResource audioRes = new ClassPathResource("bgm/" + fileName);
                    if (!audioRes.exists()) {
                        log.warn("BGM: file not found — {}", fileName);
                        continue;
                    }

                    byte[] audioBytes;
                    try (InputStream audioStream = audioRes.getInputStream()) {
                        audioBytes = audioStream.readAllBytes();
                    }

                    // Store in MinIO under system materials path
                    String subPath = "bgm/" + fileName;
                    String url = imageStorageService.storeProjectFile(
                            SYSTEM_PROJECT_ID, subPath, audioBytes, "audio/mpeg");

                    @SuppressWarnings("unchecked")
                    List<String> tags = (List<String>) track.get("tags");

                    Material material = Material.builder()
                            .type(MaterialType.AUDIO)
                            .category("BGM")
                            .name(name)
                            .url(url)
                            .tags(tags)
                            .metadata((Map<String, Object>) track.getOrDefault("metadata", Map.of()))
                            .source("UPLOADED")
                            .build();

                    materialRepository.save(material);
                    count++;
                    log.info("BGM: registered '{}' from {}", name, fileName);
                } catch (Exception e) {
                    log.error("BGM: failed to register track '{}': {}", track.get("name"), e.getMessage());
                }
            }
            log.info("BGM: initialization complete — {} tracks registered", count);
        } catch (Exception e) {
            log.error("BGM: initialization failed — {}", e.getMessage());
        }
    }
}
