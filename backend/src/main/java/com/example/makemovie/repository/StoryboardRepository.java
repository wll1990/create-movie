package com.example.makemovie.repository;

import com.example.makemovie.entity.Storyboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoryboardRepository extends JpaRepository<Storyboard, UUID> {
    Optional<Storyboard> findByProjectId(UUID projectId);
    Optional<Storyboard> findByEpisodeId(UUID episodeId);
}
