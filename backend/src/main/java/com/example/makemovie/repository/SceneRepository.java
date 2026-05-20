package com.example.makemovie.repository;

import com.example.makemovie.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SceneRepository extends JpaRepository<Scene, UUID> {
    List<Scene> findByScriptIdOrderBySceneNumber(UUID scriptId);
    void deleteByScriptId(UUID scriptId);
}
