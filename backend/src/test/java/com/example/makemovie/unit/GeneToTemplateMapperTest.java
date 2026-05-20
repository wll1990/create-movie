package com.example.makemovie.unit;

import com.example.makemovie.dto.response.CreationTemplateResponse;
import com.example.makemovie.entity.CreationTemplate;
import com.example.makemovie.entity.VideoGene;
import com.example.makemovie.repository.CreationTemplateRepository;
import com.example.makemovie.repository.VideoGeneRepository;
import com.example.makemovie.service.GeneToTemplateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeneToTemplateMapperTest {

    @Mock
    private VideoGeneRepository geneRepository;
    @Mock
    private CreationTemplateRepository templateRepository;

    private static final UUID GENE_ID = UUID.randomUUID();

    @Test
    void createTemplate_shouldMapAllFourDimensions() {
        VideoGene gene = VideoGene.builder()
                .id(GENE_ID)
                .track("都市甜宠")
                .contentGene(Map.of(
                        "tropeTags", List.of("先婚后爱"),
                        "narrativePattern", "反转递进",
                        "emotionalBeats", List.of(),
                        "dialogueDensity", 4.5
                ))
                .visualGene(Map.of(
                        "aspectRatio", "9:16",
                        "avgShotDuration", 2.3,
                        "colorPalette", "暖色调",
                        "shotSequence", List.of()
                ))
                .audioGene(Map.of(
                        "bgmStyle", "轻快电子",
                        "bgmBpm", 120,
                        "sfxTriggers", List.of(),
                        "speechRate", 4.2
                ))
                .trafficGene(Map.of(
                        "hookType", "悬念提问",
                        "hookDuration", 3,
                        "retentionSpikes", List.of(8, 22),
                        "ctaStyle", "引导关注"
                ))
                .build();

        when(geneRepository.findById(GENE_ID)).thenReturn(Optional.of(gene));
        when(templateRepository.save(any(CreationTemplate.class))).thenAnswer(inv -> {
            CreationTemplate tpl = inv.getArgument(0);
            tpl.setId(UUID.randomUUID());
            return tpl;
        });

        GeneToTemplateMapper mapper = new GeneToTemplateMapper(geneRepository, templateRepository);
        CreationTemplateResponse result = mapper.createTemplate(GENE_ID, "测试模板");

        assertThat(result.getName()).isEqualTo("测试模板");
        assertThat(result.getSourceGeneId()).isEqualTo(GENE_ID);
        assertThat(result.getNarrativeConfig()).containsKey("track");
        assertThat(result.getVisualConfig()).containsKey("colorPalette");
        assertThat(result.getAudioConfig()).containsKey("bgmStyle");
        assertThat(result.getPacingConfig()).containsKey("hookType");
    }

    @Test
    void createTemplateWithInheritance_shouldOnlyInheritSelectedDimensions() {
        VideoGene gene = VideoGene.builder()
                .id(GENE_ID)
                .track("悬疑")
                .contentGene(Map.of("tropeTags", List.of("身份反转"), "narrativePattern", ""))
                .visualGene(Map.of("colorPalette", "冷色调", "avgShotDuration", 1.8))
                .audioGene(Map.of("bgmStyle", "紧张"))
                .trafficGene(Map.of("hookType", "惊吓"))
                .build();

        when(geneRepository.findById(GENE_ID)).thenReturn(Optional.of(gene));
        when(templateRepository.save(any())).thenAnswer(inv -> {
            CreationTemplate tpl = inv.getArgument(0);
            tpl.setId(UUID.randomUUID());
            return tpl;
        });

        GeneToTemplateMapper mapper = new GeneToTemplateMapper(geneRepository, templateRepository);

        // Only inherit narrative and pacing, override visual and audio
        Map<String, Boolean> inheritance = Map.of(
                "narrative", true, "visual", false, "audio", false, "pacing", true);
        Map<String, Map<String, Object>> overrides = Map.of(
                "visual", Map.of("aspectRatio", "16:9"),
                "audio", Map.of("bgmBpm", 140));

        CreationTemplateResponse result = mapper.createTemplateWithInheritance(
                GENE_ID, "选择性模板", inheritance, overrides);

        // Inherited from gene
        assertThat(result.getNarrativeConfig()).containsKey("tropeTags");
        assertThat(result.getPacingConfig()).containsEntry("hookType", "惊吓");
        // Overridden
        assertThat(result.getVisualConfig()).containsEntry("aspectRatio", "16:9");
        assertThat(result.getAudioConfig()).containsEntry("bgmBpm", 140);
        // Visual should NOT have colorPalette (overridden)
        assertThat(result.getVisualConfig()).doesNotContainKey("colorPalette");
    }

    @Test
    void createTemplate_shouldThrow_whenGeneNotFound() {
        when(geneRepository.findById(GENE_ID)).thenReturn(Optional.empty());

        GeneToTemplateMapper mapper = new GeneToTemplateMapper(geneRepository, templateRepository);

        assertThatThrownBy(() -> mapper.createTemplate(GENE_ID, "test"))
                .isInstanceOf(com.example.makemovie.exception.BusinessException.class)
                .hasMessageContaining("视频基因不存在");
    }
}
