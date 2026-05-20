package com.example.makemovie.repository;

import com.example.makemovie.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScriptRepository extends JpaRepository<Script, UUID> {
    Optional<Script> findByProjectId(UUID projectId);
    Optional<Script> findByEpisodeId(UUID episodeId);
}
