package com.example.makemovie.dto.response;

import com.example.makemovie.entity.CreationTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreationTemplateResponse {
    private UUID id;
    private UUID sourceGeneId;
    private String name;
    private Map<String, Object> narrativeConfig;
    private Map<String, Object> visualConfig;
    private Map<String, Object> audioConfig;
    private Map<String, Object> pacingConfig;
    private Boolean editable;
    private Boolean isDefault;
    private LocalDateTime createdAt;

    public static CreationTemplateResponse fromEntity(CreationTemplate tpl) {
        return CreationTemplateResponse.builder()
                .id(tpl.getId())
                .sourceGeneId(tpl.getSourceGeneId())
                .name(tpl.getName())
                .narrativeConfig(tpl.getNarrativeConfig())
                .visualConfig(tpl.getVisualConfig())
                .audioConfig(tpl.getAudioConfig())
                .pacingConfig(tpl.getPacingConfig())
                .editable(tpl.getEditable())
                .isDefault(tpl.getIsDefault())
                .createdAt(tpl.getCreatedAt())
                .build();
    }
}
