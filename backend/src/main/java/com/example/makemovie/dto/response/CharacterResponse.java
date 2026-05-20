package com.example.makemovie.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterResponse {
    private UUID id;
    private UUID projectId;
    private String name;
    private String role;
    private String gender;
    private String ageRange;
    private String personality;
    private Map<String, Object> appearance;
    private List<Map<String, Object>> expressions;
    private Map<String, Object> voiceConfig;
    private List<Map<String, Object>> candidatePortraits;
    private Integer selectedPortraitIndex;
    private String imageGenerationStatus;
    private LocalDateTime createdAt;

    public static CharacterResponse fromEntity(com.example.makemovie.entity.Character entity) {
        return CharacterResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .role(entity.getRole())
                .gender(entity.getGender())
                .ageRange(entity.getAgeRange())
                .personality(entity.getPersonality())
                .appearance(entity.getAppearance())
                .expressions(entity.getExpressions())
                .voiceConfig(entity.getVoiceConfig())
                .candidatePortraits(entity.getCandidatePortraits())
                .selectedPortraitIndex(entity.getSelectedPortraitIndex())
                .imageGenerationStatus(entity.getImageGenerationStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
