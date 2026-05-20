package com.example.makemovie.repository;

import com.example.makemovie.entity.WorkflowLog;
import com.example.makemovie.enums.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowLogRepository extends JpaRepository<WorkflowLog, UUID> {
    List<WorkflowLog> findByProjectIdOrderByCreatedAt(UUID projectId);
    Optional<WorkflowLog> findFirstByProjectIdAndStepOrderByCreatedAtDesc(UUID projectId, WorkflowStep step);
    Optional<WorkflowLog> findByProjectIdAndStep(UUID projectId, WorkflowStep step);
    List<WorkflowLog> findByEpisodeIdOrderByCreatedAt(UUID episodeId);
    Optional<WorkflowLog> findByEpisodeIdAndStep(UUID episodeId, WorkflowStep step);
}
