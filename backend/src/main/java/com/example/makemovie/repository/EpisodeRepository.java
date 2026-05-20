package com.example.makemovie.repository;

import com.example.makemovie.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EpisodeRepository extends JpaRepository<Episode, UUID> {

    List<Episode> findByProjectIdOrderByEpisodeNumber(UUID projectId);

    Optional<Episode> findByProjectIdAndEpisodeNumber(UUID projectId, Integer episodeNumber);

    long countByProjectId(UUID projectId);
}
