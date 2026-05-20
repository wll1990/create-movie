package com.example.makemovie.service;

import com.example.makemovie.dto.response.CreationTemplateResponse;
import com.example.makemovie.entity.CreationTemplate;
import com.example.makemovie.entity.VideoGene;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CreationTemplateRepository;
import com.example.makemovie.repository.VideoGeneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maps VideoGene (raw analysis) → CreationTemplate (config for creation engine).
 *
 * The template is used by ScriptService, StoryboardService etc. to guide generation.
 * Users can selectively inherit dimensions (narrative, visual, audio, pacing).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneToTemplateMapper {

    private final VideoGeneRepository geneRepository;
    private final CreationTemplateRepository templateRepository;

    @Transactional
    public CreationTemplateResponse createTemplate(UUID geneId, String name) {
        VideoGene gene = geneRepository.findById(geneId)
                .orElseThrow(() -> new BusinessException("GENE_NOT_FOUND",
                        "视频基因不存在: " + geneId));

        CreationTemplate template = CreationTemplate.builder()
                .sourceGeneId(geneId)
                .name(name != null ? name : "模板-" + gene.getTrack())
                .narrativeConfig(Map.of(
                        "track", gene.getTrack(),
                        "tropeTags", gene.getContentGene().getOrDefault("tropeTags", List.of()),
                        "narrativePattern", gene.getContentGene().getOrDefault("narrativePattern", ""),
                        "emotionalBeats", gene.getContentGene().getOrDefault("emotionalBeats", List.of()),
                        "dialogueDensity", gene.getContentGene().getOrDefault("dialogueDensity", 4.0)
                ))
                .visualConfig(Map.of(
                        "aspectRatio", gene.getVisualGene().getOrDefault("aspectRatio", "9:16"),
                        "colorPalette", gene.getVisualGene().getOrDefault("colorPalette", ""),
                        "shotRhythm", Map.of(
                                "avgDuration", gene.getVisualGene().getOrDefault("avgShotDuration", 2.5),
                                "shotSequence", gene.getVisualGene().getOrDefault("shotSequence", List.of())
                        ),
                        "textOverlayRatio", gene.getVisualGene().getOrDefault("textOverlayRatio", 0.15)
                ))
                .audioConfig(Map.of(
                        "bgmStyle", gene.getAudioGene().getOrDefault("bgmStyle", ""),
                        "bgmBpm", gene.getAudioGene().getOrDefault("bgmBpm", 120),
                        "sfxTriggers", gene.getAudioGene().getOrDefault("sfxTriggers", List.of()),
                        "speechRate", gene.getAudioGene().getOrDefault("speechRate", 4.0)
                ))
                .pacingConfig(Map.of(
                        "hookType", gene.getTrafficGene().getOrDefault("hookType", ""),
                        "hookDuration", gene.getTrafficGene().getOrDefault("hookDuration", 3),
                        "beatInterval", 5,
                        "climaxPosition", 0.7
                ))
                .editable(true)
                .isDefault(false)
                .build();

        template = templateRepository.save(template);
        log.info("CreationTemplate created: id={}, name={}, sourceGeneId={}",
                template.getId(), template.getName(), geneId);
        return CreationTemplateResponse.fromEntity(template);
    }

    /**
     * Create a template with selective inheritance.
     *
     * @param geneId Source gene ID
     * @param name Template name
     * @param inheritance Map of dimension → whether to inherit
     *        e.g. {"narrative": true, "visual": true, "audio": false, "pacing": true}
     * @param overrides Manual overrides for non-inherited dimensions
     */
    @Transactional
    public CreationTemplateResponse createTemplateWithInheritance(
            UUID geneId, String name,
            Map<String, Boolean> inheritance,
            Map<String, Map<String, Object>> overrides) {
        VideoGene gene = geneRepository.findById(geneId)
                .orElseThrow(() -> new BusinessException("GENE_NOT_FOUND",
                        "视频基因不存在: " + geneId));

        boolean inhNarrative = inheritance.getOrDefault("narrative", true);
        boolean inhVisual = inheritance.getOrDefault("visual", true);
        boolean inhAudio = inheritance.getOrDefault("audio", true);
        boolean inhPacing = inheritance.getOrDefault("pacing", true);

        Map<String, Object> narrativeConfig = inhNarrative
                ? Map.of(
                    "track", gene.getTrack(),
                    "tropeTags", gene.getContentGene().getOrDefault("tropeTags", List.of()),
                    "narrativePattern", gene.getContentGene().getOrDefault("narrativePattern", ""),
                    "emotionalBeats", gene.getContentGene().getOrDefault("emotionalBeats", List.of()))
                : overrides.getOrDefault("narrative", Map.of("track", "都市甜宠"));

        Map<String, Object> visualConfig = inhVisual
                ? Map.of(
                    "aspectRatio", gene.getVisualGene().getOrDefault("aspectRatio", "9:16"),
                    "colorPalette", gene.getVisualGene().getOrDefault("colorPalette", ""),
                    "avgShotDuration", gene.getVisualGene().getOrDefault("avgShotDuration", 2.5))
                : overrides.getOrDefault("visual", Map.of("aspectRatio", "9:16"));

        Map<String, Object> audioConfig = inhAudio
                ? Map.of(
                    "bgmStyle", gene.getAudioGene().getOrDefault("bgmStyle", ""),
                    "bgmBpm", gene.getAudioGene().getOrDefault("bgmBpm", 120),
                    "speechRate", gene.getAudioGene().getOrDefault("speechRate", 4.0))
                : overrides.getOrDefault("audio", Map.of("bgmBpm", 120));

        Map<String, Object> pacingConfig = inhPacing
                ? Map.of(
                    "hookType", gene.getTrafficGene().getOrDefault("hookType", ""),
                    "hookDuration", gene.getTrafficGene().getOrDefault("hookDuration", 3))
                : overrides.getOrDefault("pacing", Map.of("hookDuration", 3));

        CreationTemplate template = CreationTemplate.builder()
                .sourceGeneId(geneId)
                .name(name)
                .narrativeConfig(narrativeConfig)
                .visualConfig(visualConfig)
                .audioConfig(audioConfig)
                .pacingConfig(pacingConfig)
                .editable(true)
                .isDefault(false)
                .build();

        template = templateRepository.save(template);
        log.info("Template created with selective inheritance: id={}, narrative={}, visual={}, audio={}, pacing={}",
                template.getId(), inhNarrative, inhVisual, inhAudio, inhPacing);
        return CreationTemplateResponse.fromEntity(template);
    }

    public List<CreationTemplateResponse> listTemplates() {
        return templateRepository.findAll()
                .stream()
                .map(CreationTemplateResponse::fromEntity)
                .toList();
    }

    public List<CreationTemplateResponse> getDefaultTemplates() {
        return templateRepository.findByIsDefaultTrue()
                .stream()
                .map(CreationTemplateResponse::fromEntity)
                .toList();
    }
}
