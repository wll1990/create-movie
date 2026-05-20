package com.example.makemovie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "composition")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Composition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID projectId;

    private UUID episodeId;

    @Column(nullable = false)
    private UUID storyboardId;

    @Column(length = 500)
    private String videoUrl;

    @Column(length = 500)
    private String coverUrl;

    private UUID bgmMaterialId;

    private Integer durationSec;

    @Column(length = 20)
    @Builder.Default
    private String resolution = "1080x1920";

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Builder.Default
    private Integer progress = 0;

    @Column(length = 30)
    @Builder.Default
    private String compositionType = "LEGACY";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> compositionConfig = Map.of();

    @Column(updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
