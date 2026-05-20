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
public class StoryboardResponse {
    private UUID id;
    private UUID projectId;
    private UUID scriptId;
    private Integer totalFrames;
    private String status;
    private List<StoryboardFrameResponse> frames;
    private LocalDateTime createdAt;
}
