package com.example.makemovie.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "storyboard")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Storyboard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    private UUID episodeId;

    @Column(nullable = false)
    private UUID scriptId;

    @Builder.Default
    private Integer totalFrames = 0;

    @Column(length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
