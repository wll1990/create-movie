package com.example.makemovie.dto.response;

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
public class ScriptResponse {
    private UUID id;
    private UUID projectId;
    private String title;
    private String track;
    private Integer duration;
    private Map<String, Object> content;
    private Integer version;
    private String status;
    private LocalDateTime createdAt;

    public static ScriptResponse fromEntity(com.example.makemovie.entity.Script script) {
        return ScriptResponse.builder()
                .id(script.getId())
                .projectId(script.getProjectId())
                .title(script.getTitle())
                .track(script.getTrack())
                .duration(script.getDuration())
                .content(script.getContent())
                .version(script.getVersion())
                .status(script.getStatus())
                .createdAt(script.getCreatedAt())
                .build();
    }
}
