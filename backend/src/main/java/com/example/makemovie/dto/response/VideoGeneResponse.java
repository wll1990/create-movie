package com.example.makemovie.dto.response;

import com.example.makemovie.entity.VideoGene;
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
public class VideoGeneResponse {
    private UUID id;
    private UUID projectId;
    private String track;
    private Map<String, Object> contentGene;
    private Map<String, Object> visualGene;
    private Map<String, Object> audioGene;
    private Map<String, Object> trafficGene;
    private LocalDateTime createdAt;

    public static VideoGeneResponse fromEntity(VideoGene gene) {
        return VideoGeneResponse.builder()
                .id(gene.getId())
                .projectId(gene.getProjectId())
                .track(gene.getTrack())
                .contentGene(gene.getContentGene())
                .visualGene(gene.getVisualGene())
                .audioGene(gene.getAudioGene())
                .trafficGene(gene.getTrafficGene())
                .createdAt(gene.getCreatedAt())
                .build();
    }
}
