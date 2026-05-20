package com.example.makemovie.dto.response;

import com.example.makemovie.enums.ProjectMode;
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
public class ProjectResponse {
    private UUID id;
    private String title;
    private String track;
    private String theme;
    private ProjectMode mode;
    private String status;
    private UUID sourceVideoGeneId;
    private UUID creationTemplateId;
    private Map<String, Object> progress;
    private long episodeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
