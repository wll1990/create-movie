package com.example.makemovie.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryboardFrameResponse {
    private UUID id;
    private UUID sceneId;
    private Integer frameNumber;
    private String shotType;
    private String cameraAngle;
    private String bgDescription;
    private String bgImageUrl;
    private List<Map<String, Object>> characters;
    private UUID dialogueId;
    private String subtitleText;
    private Double durationSec;
    private String transition;
    private String status;

    // Video generation fields
    private String clipPrompt;
    private String clipStatus;
    private String clipVideoUrl;
}
