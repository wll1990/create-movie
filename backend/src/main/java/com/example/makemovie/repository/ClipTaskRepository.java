package com.example.makemovie.repository;

import com.example.makemovie.entity.ClipTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClipTaskRepository extends JpaRepository<ClipTask, UUID> {

    List<ClipTask> findByStatusOrderByCreatedAt(String status, Pageable pageable);

    List<ClipTask> findByProjectIdOrderByFrameNumber(UUID projectId);

    List<ClipTask> findByEpisodeIdOrderByFrameNumber(UUID episodeId);

    Optional<ClipTask> findByStoryboardFrameId(UUID storyboardFrameId);

    long countByProjectIdAndStatus(UUID projectId, String status);

    long countByEpisodeIdAndStatus(UUID episodeId, String status);
}
