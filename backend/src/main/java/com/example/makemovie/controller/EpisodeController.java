package com.example.makemovie.controller;

import com.example.makemovie.entity.Episode;
import com.example.makemovie.service.EpisodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class EpisodeController {

    private final EpisodeService episodeService;

    @GetMapping("/api/projects/{projectId}/episodes")
    public ResponseEntity<List<Map<String, Object>>> listEpisodes(
            @PathVariable UUID projectId) {
        List<Episode> episodes = episodeService.listEpisodes(projectId);
        return ResponseEntity.ok(episodes.stream()
                .map(this::toMap)
                .collect(Collectors.toList()));
    }

    @PostMapping("/api/projects/{projectId}/episodes")
    public ResponseEntity<Map<String, Object>> createEpisode(
            @PathVariable UUID projectId,
            @RequestBody Map<String, Object> body) {
        String title = (String) body.getOrDefault("title", "");
        int number = body.containsKey("episodeNumber")
                ? ((Number) body.get("episodeNumber")).intValue()
                : 0;
        if (number <= 0) {
            number = (int) episodeService.getEpisodeCount(projectId) + 1;
        }
        Episode episode = episodeService.createEpisode(projectId, number, title);
        return ResponseEntity.ok(toMap(episode));
    }

    @PostMapping("/api/projects/{projectId}/episodes/next")
    public ResponseEntity<Map<String, Object>> createNextEpisode(
            @PathVariable UUID projectId) {
        Episode episode = episodeService.createNextEpisode(projectId);
        return ResponseEntity.ok(toMap(episode));
    }

    @GetMapping("/api/episodes/{episodeId}")
    public ResponseEntity<Map<String, Object>> getEpisode(
            @PathVariable UUID episodeId) {
        Episode episode = episodeService.getEpisode(episodeId);
        return ResponseEntity.ok(toMap(episode));
    }

    private Map<String, Object> toMap(Episode ep) {
        return Map.of(
                "id", ep.getId(),
                "projectId", ep.getProjectId(),
                "episodeNumber", ep.getEpisodeNumber(),
                "title", ep.getTitle(),
                "status", ep.getStatus(),
                "progress", ep.getProgress(),
                "createdAt", ep.getCreatedAt()
        );
    }
}
