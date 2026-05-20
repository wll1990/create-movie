package com.example.makemovie.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "composition_task")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class CompositionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID compositionId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "QUEUED";

    @Builder.Default
    private Integer progress = 0;

    @Builder.Default
    private Integer currentFrame = 0;

    @Builder.Default
    private Integer totalFrames = 0;

    @Column(columnDefinition = "TEXT")
    private String ffmpegCommand;

    @Column(columnDefinition = "TEXT")
    private String ffmpegLog;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
