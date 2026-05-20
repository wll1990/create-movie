package com.example.makemovie.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "image_gen_task", indexes = {
    @Index(name = "idx_igt_status", columnList = "status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ImageGenTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    @Column
    private UUID charId;

    @Column(length = 30, nullable = false)
    private String taskType; // PORTRAIT, THREEVIEW, EXPRESSION, BACKGROUND

    @Column(length = 100)
    private String externalTaskId;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(length = 500)
    private String callbackKey; // identifies what to update on completion

    @Column(length = 1000)
    private String resultUrl;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Builder.Default
    private int pollCount = 0;

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
