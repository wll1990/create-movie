package com.example.makemovie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "clip_task")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClipTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID storyboardFrameId;

    @Column(nullable = false)
    private UUID projectId;

    private UUID episodeId;

    @Column(nullable = false)
    private Integer frameNumber;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(columnDefinition = "TEXT")
    private String clipPrompt;

    @Column(columnDefinition = "TEXT")
    private String referenceImageUrl;

    @Column(columnDefinition = "TEXT")
    private String expressionImageUrl;

    @Column(columnDefinition = "TEXT")
    private String backgroundImageUrl;

    @Column(length = 500)
    private String videoUrl;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> modelParams = Map.of();

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime approvedAt;
}
