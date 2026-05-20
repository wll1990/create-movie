package com.example.makemovie.repository;

import com.example.makemovie.entity.Composition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompositionRepository extends JpaRepository<Composition, UUID> {
    List<Composition> findByProjectId(UUID projectId);
    List<Composition> findByEpisodeId(UUID episodeId);
}
