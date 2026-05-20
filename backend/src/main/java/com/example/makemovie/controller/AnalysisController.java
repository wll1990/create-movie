package com.example.makemovie.controller;

import com.example.makemovie.dto.response.CreationTemplateResponse;
import com.example.makemovie.dto.response.VideoGeneResponse;
import com.example.makemovie.service.GeneToTemplateMapper;
import com.example.makemovie.service.VideoAnalyzerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AnalysisController {

    private final VideoAnalyzerService videoAnalyzerService;
    private final GeneToTemplateMapper geneToTemplateMapper;

    /**
     * Upload a video and trigger gene analysis.
     */
    @PostMapping("/projects/{projectId}/analyze")
    public ResponseEntity<VideoGeneResponse> analyzeVideo(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(videoAnalyzerService.analyze(projectId, file));
    }

    /**
     * Get the VideoGene analysis result for a project.
     */
    @GetMapping("/projects/{projectId}/gene")
    public ResponseEntity<VideoGeneResponse> getGene(@PathVariable UUID projectId) {
        return ResponseEntity.ok(videoAnalyzerService.getGene(projectId));
    }

    /**
     * Create a CreationTemplate from a VideoGene.
     */
    @PostMapping("/genes/{geneId}/template")
    public ResponseEntity<CreationTemplateResponse> createTemplate(
            @PathVariable UUID geneId,
            @RequestBody Map<String, Object> request) {
        String name = (String) request.getOrDefault("name", "默认");

        @SuppressWarnings("unchecked")
        Map<String, Boolean> inheritance =
                (Map<String, Boolean>) request.get("inheritance");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> overrides =
                (Map<String, Map<String, Object>>) request.get("overrides");

        if (inheritance != null && !inheritance.isEmpty()) {
            return ResponseEntity.ok(geneToTemplateMapper.createTemplateWithInheritance(
                    geneId, name, inheritance,
                    overrides != null ? overrides : Map.of()));
        }

        return ResponseEntity.ok(geneToTemplateMapper.createTemplate(geneId, name));
    }

    /**
     * List all creation templates.
     */
    @GetMapping("/templates")
    public ResponseEntity<java.util.List<CreationTemplateResponse>> listTemplates() {
        return ResponseEntity.ok(geneToTemplateMapper.listTemplates());
    }

    /**
     * List default templates.
     */
    @GetMapping("/templates/defaults")
    public ResponseEntity<java.util.List<CreationTemplateResponse>> listDefaultTemplates() {
        return ResponseEntity.ok(geneToTemplateMapper.getDefaultTemplates());
    }
}
