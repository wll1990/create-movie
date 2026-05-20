package com.example.makemovie.controller;

import com.example.makemovie.client.TtsClient;
import com.example.makemovie.dto.response.CharacterResponse;
import com.example.makemovie.entity.WorkflowLog;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.WorkflowLogRepository;
import com.example.makemovie.service.CharacterImageService;
import com.example.makemovie.service.CharacterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class CharacterController {

    private final CharacterService characterService;
    private final CharacterImageService characterImageService;
    private final CharacterRepository characterRepository;
    private final WorkflowLogRepository workflowLogRepository;
    private final TtsClient ttsClient;
    private final ObjectMapper objectMapper;

    // --- Character CRUD ---

    @PostMapping("/api/projects/{projectId}/characters")
    public ResponseEntity<List<CharacterResponse>> generateCharacters(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(characterService.generateCharacters(projectId));
    }

    @GetMapping("/api/projects/{projectId}/characters")
    public ResponseEntity<List<CharacterResponse>> getCharacters(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(characterService.getCharacters(projectId));
    }

    @PutMapping("/api/projects/{projectId}/characters/{charId}/voice")
    public ResponseEntity<Map<String, Object>> updateVoiceConfig(
            @PathVariable UUID projectId,
            @PathVariable UUID charId,
            @RequestBody Map<String, Object> voiceConfig) {
        var character = characterRepository.findById(charId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + charId));
        character.setVoiceConfig(voiceConfig);
        characterRepository.save(character);
        return ResponseEntity.ok(Map.of("status", "ok", "voiceConfig", voiceConfig));
    }

    // --- Per-character prompts ---

    @GetMapping("/api/projects/{projectId}/characters/prompts")
    public ResponseEntity<List<Map<String, Object>>> getCharacterPrompts(
            @PathVariable UUID projectId) {
        Optional<WorkflowLog> wfLog = workflowLogRepository
                .findByProjectIdAndStep(projectId, WorkflowStep.CHARACTER_DESIGN);
        if (wfLog.isEmpty() || wfLog.get().getPrompt() == null) {
            return ResponseEntity.ok(List.of());
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> promptStruct = objectMapper.readValue(
                    wfLog.get().getPrompt(), Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> characters =
                    (List<Map<String, Object>>) promptStruct.get("characters");
            return ResponseEntity.ok(characters != null ? characters : List.of());
        } catch (Exception e) {
            // Fallback: return as single entry for old format
            return ResponseEntity.ok(List.of(
                    Map.<String, Object>of("name", "全部角色", "prompt", wfLog.get().getPrompt())
            ));
        }
    }

    @PutMapping("/api/projects/{projectId}/characters/{charId}/regenerate")
    public ResponseEntity<CharacterResponse> regenerateCharacter(
            @PathVariable UUID projectId,
            @PathVariable UUID charId,
            @RequestBody(required = false) Map<String, Object> body) {
        String customPrompt = body != null && body.containsKey("prompt")
                ? (String) body.get("prompt") : null;
        return ResponseEntity.ok(characterService.regenerateCharacter(projectId, charId, customPrompt));
    }

    // --- Gacha-style async image generation ---

    @GetMapping("/api/projects/{projectId}/characters/{charId}/candidates")
    public ResponseEntity<Map<String, Object>> getCandidates(
            @PathVariable UUID projectId,
            @PathVariable UUID charId) {
        Map<String, Object> status = characterImageService.getImageStatus(charId);
        return ResponseEntity.ok(Map.of(
                "candidatePortraits", status.get("candidatePortraits"),
                "imageGenerationStatus", status.get("imageGenerationStatus"),
                "selectedPortraitIndex", status.get("selectedPortraitIndex")
        ));
    }

    @PutMapping("/api/projects/{projectId}/characters/{charId}/select-portrait")
    public ResponseEntity<Map<String, Object>> selectPortrait(
            @PathVariable UUID projectId,
            @PathVariable UUID charId,
            @RequestBody Map<String, Object> body) {
        int candidateIndex = body.containsKey("candidateIndex")
                ? ((Number) body.get("candidateIndex")).intValue() : 0;
        characterImageService.selectPortrait(projectId, charId, candidateIndex);
        return ResponseEntity.ok(Map.of(
                "message", "已选择候选立绘，正在生成三视图和表情",
                "selectedPortraitIndex", candidateIndex
        ));
    }

    @GetMapping("/api/projects/{projectId}/characters/{charId}/image-status")
    public ResponseEntity<Map<String, Object>> getImageStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID charId) {
        return ResponseEntity.ok(characterImageService.getImageStatus(charId));
    }

    // --- Three-View endpoints ---

    @GetMapping("/api/projects/{projectId}/characters/{charId}/threeview")
    public ResponseEntity<Map<String, Object>> getThreeViewStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID charId) {
        return ResponseEntity.ok(characterImageService.getThreeViewStatus(charId));
    }

    @PostMapping("/api/projects/{projectId}/characters/{charId}/threeview/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateThreeView(
            @PathVariable UUID projectId,
            @PathVariable UUID charId) {
        characterImageService.regenerateThreeView(projectId, charId);
        return ResponseEntity.ok(Map.of(
                "message", "三视图重新生成中",
                "charId", charId.toString()
        ));
    }

    // --- Expression candidates endpoints ---

    @GetMapping("/api/projects/{projectId}/characters/{charId}/expressions/candidates")
    public ResponseEntity<Map<String, Object>> getExpressionCandidates(
            @PathVariable UUID projectId,
            @PathVariable UUID charId) {
        return ResponseEntity.ok(characterImageService.getExpressionCandidates(charId));
    }

    @PutMapping("/api/projects/{projectId}/characters/{charId}/expressions/select")
    public ResponseEntity<Map<String, Object>> selectExpression(
            @PathVariable UUID projectId,
            @PathVariable UUID charId,
            @RequestBody Map<String, Object> body) {
        String emotionType = (String) body.get("emotionType");
        int candidateIndex = body.containsKey("candidateIndex")
                ? ((Number) body.get("candidateIndex")).intValue() : 0;
        characterImageService.selectExpression(projectId, charId, emotionType, candidateIndex);
        return ResponseEntity.ok(Map.of(
                "message", "已选择" + emotionType + "表情候选",
                "emotionType", emotionType,
                "selectedIndex", candidateIndex
        ));
    }

    @PostMapping("/api/projects/{projectId}/characters/{charId}/expressions/regenerate")
    public ResponseEntity<Map<String, Object>> regenerateExpressions(
            @PathVariable UUID projectId,
            @PathVariable UUID charId) {
        characterImageService.regenerateExpressions(projectId, charId);
        return ResponseEntity.ok(Map.of(
                "message", "表情重新抽卡中",
                "charId", charId.toString()
        ));
    }

    // --- TTS Voices ---

    @GetMapping("/api/tts/voices")
    public ResponseEntity<List<Map<String, String>>> listVoices() {
        return ResponseEntity.ok(ttsClient.listVoices());
    }
}
