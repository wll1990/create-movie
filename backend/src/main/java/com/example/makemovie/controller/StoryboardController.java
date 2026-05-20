package com.example.makemovie.controller;

import com.example.makemovie.dto.response.StoryboardFrameResponse;
import com.example.makemovie.dto.response.StoryboardResponse;
import com.example.makemovie.service.StoryboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/storyboards")
@RequiredArgsConstructor
public class StoryboardController {

    private final StoryboardService storyboardService;

    @PostMapping
    public ResponseEntity<StoryboardResponse> generateStoryboard(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(storyboardService.generateStoryboard(projectId));
    }

    @GetMapping
    public ResponseEntity<StoryboardResponse> getStoryboard(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(storyboardService.getStoryboard(projectId));
    }

    // --- Frame editing ---

    @PutMapping("/frames/{frameId}")
    public ResponseEntity<StoryboardFrameResponse> updateFrame(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId,
            @RequestBody Map<String, Object> updates) {
        return ResponseEntity.ok(storyboardService.updateFrame(frameId, updates));
    }

    // --- Regeneration ---

    @PostMapping("/regenerate")
    public ResponseEntity<StoryboardResponse> regenerateStoryboard(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(storyboardService.regenerateStoryboard(projectId));
    }

    @PostMapping("/frames/{frameId}/regenerate")
    public ResponseEntity<StoryboardFrameResponse> regenerateFrame(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId,
            @RequestBody(required = false) Map<String, Object> body) {
        String customPrompt = body != null && body.containsKey("customPrompt")
                ? (String) body.get("customPrompt") : null;
        return ResponseEntity.ok(storyboardService.regenerateFrame(projectId, frameId, customPrompt));
    }

    // --- Prompt viewing ---

    @GetMapping("/prompt")
    public ResponseEntity<Map<String, Object>> getStoryboardPrompt(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(storyboardService.getStoryboardPrompt(projectId));
    }

    @GetMapping("/frames/{frameId}/prompt")
    public ResponseEntity<Map<String, Object>> getFramePrompt(
            @PathVariable UUID projectId,
            @PathVariable UUID frameId) {
        return ResponseEntity.ok(storyboardService.getFramePrompt(frameId));
    }
}
