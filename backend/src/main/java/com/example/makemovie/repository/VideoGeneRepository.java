package com.example.makemovie.repository;

import com.example.makemovie.entity.VideoGene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VideoGeneRepository extends JpaRepository<VideoGene, UUID> {
    Optional<VideoGene> findByProjectId(UUID projectId);
}
