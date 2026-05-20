package com.example.makemovie.repository;

import com.example.makemovie.entity.Project;
import com.example.makemovie.enums.ProjectMode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByStatus(String status, Pageable pageable);
    Page<Project> findByMode(ProjectMode mode, Pageable pageable);
    Page<Project> findByTrack(String track, Pageable pageable);
}
