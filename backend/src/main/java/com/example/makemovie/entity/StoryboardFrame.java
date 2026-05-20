package com.example.makemovie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "storyboard_frame")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class StoryboardFrame {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID storyboardId;

    @Column(nullable = false)
    private UUID sceneId;

    @Column(nullable = false)
    private Integer frameNumber;

    @Column(length = 50)
    private String shotType;

    @Column(length = 50)
    private String cameraAngle;

    @Column(columnDefinition = "TEXT")
    private String bgDescription;

    @Column(length = 500)
    private String bgImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> characters = List.of();

    private UUID dialogueId;

    @Column(columnDefinition = "TEXT")
    private String subtitleText;

    @Builder.Default
    private Double durationSec = 3.0;

    @Column(length = 50)
    @Builder.Default
    private String transition = "cut";

    @Column(length = 20)
    @Builder.Default
    private String status = "DRAFT";

    // --- Video generation fields (V2) ---

    @Column(length = 500)
    private String clipVideoUrl;

    @Column(length = 500)
    private String voiceAudioUrl;

    @Column(columnDefinition = "TEXT")
    private String clipPrompt;

    @Column(length = 20)
    @Builder.Default
    private String clipStatus = "PENDING";

    @Builder.Default
    private Integer clipRetryCount = 0;

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
