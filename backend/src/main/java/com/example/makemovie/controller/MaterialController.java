package com.example.makemovie.controller;

import com.example.makemovie.entity.Material;
import com.example.makemovie.enums.MaterialType;
import com.example.makemovie.repository.MaterialRepository;
import com.example.makemovie.service.VideoComposerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialRepository materialRepository;
    private final VideoComposerService videoComposerService;

    @GetMapping
    public ResponseEntity<List<Material>> listMaterials(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category) {
        List<Material> results;
        if (type != null && category != null) {
            MaterialType materialType = MaterialType.valueOf(type.toUpperCase());
            results = materialRepository.findByType(materialType).stream()
                    .filter(m -> category.equals(m.getCategory()))
                    .toList();
        } else if (type != null) {
            MaterialType materialType = MaterialType.valueOf(type.toUpperCase());
            results = materialRepository.findByType(materialType);
        } else if (category != null) {
            results = materialRepository.findByCategory(category);
        } else {
            results = materialRepository.findAll();
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<InputStreamResource> getFile(@PathVariable UUID id) {
        Material material = materialRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("素材不存在: " + id));

        String url = material.getUrl();
        if (url == null || url.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        try {
            InputStream stream;
            String contentType = "application/octet-stream";

            if (url.startsWith("minio://") || url.startsWith("file://")) {
                var localPath = java.nio.file.Path.of(
                        videoComposerService.resolveToLocalPathStr(url));
                stream = java.nio.file.Files.newInputStream(localPath);
                if (url.endsWith(".mp3")) {
                    contentType = "audio/mpeg";
                } else if (url.endsWith(".mp4")) {
                    contentType = "video/mp4";
                }
            } else if (url.startsWith("http")) {
                stream = URI.create(url).toURL().openStream();
            } else {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + material.getName() + "\"")
                    .body(new InputStreamResource(stream));
        } catch (Exception e) {
            log.error("Failed to stream material file {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/bgm")
    public ResponseEntity<List<Map<String, Object>>> listBgm() {
        List<Material> bgmMaterials = materialRepository.findByCategory("BGM");
        List<Map<String, Object>> result = bgmMaterials.stream()
                .map(m -> Map.<String, Object>of(
                        "id", m.getId().toString(),
                        "name", m.getName(),
                        "tags", m.getTags() != null ? m.getTags() : List.of(),
                        "metadata", m.getMetadata()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping
    public String uploadMaterial() {
        return "not implemented";
    }
}
