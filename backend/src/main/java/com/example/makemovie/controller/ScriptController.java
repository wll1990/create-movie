package com.example.makemovie.controller;

import com.example.makemovie.dto.request.ScriptGenerateRequest;
import com.example.makemovie.dto.request.ScriptUpdateRequest;
import com.example.makemovie.dto.response.ScriptResponse;
import com.example.makemovie.service.ScriptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/projects/{projectId}/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    @PostMapping
    public ResponseEntity<ScriptResponse> generateScript(
            @PathVariable UUID projectId,
            @Valid @RequestBody ScriptGenerateRequest request) {

        List<Map<String, Object>> characters = null;
        if (request.getCharacters() != null && !request.getCharacters().isEmpty()) {
            characters = request.getCharacters().stream()
                    .map(c -> Map.<String, Object>of(
                            "name", c.getName(),
                            "role", c.getRole() != null ? c.getRole() : "",
                            "personality", c.getPersonality() != null ? c.getPersonality() : ""
                    ))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(
                scriptService.generateScript(
                        projectId,
                        request.getTrack(),
                        request.getTheme(),
                        request.getDuration(),
                        characters));
    }

    @GetMapping
    public ResponseEntity<ScriptResponse> getScript(@PathVariable UUID projectId) {
        return ResponseEntity.ok(scriptService.getScript(projectId));
    }

    @PutMapping
    public ResponseEntity<ScriptResponse> updateScript(
            @PathVariable UUID projectId,
            @Valid @RequestBody ScriptUpdateRequest request) {
        return ResponseEntity.ok(
                scriptService.updateScript(projectId, request.getTitle(), request.getContent()));
    }
}
