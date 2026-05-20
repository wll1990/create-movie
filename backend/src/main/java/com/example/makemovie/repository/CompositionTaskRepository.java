package com.example.makemovie.repository;

import com.example.makemovie.entity.CompositionTask;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompositionTaskRepository extends JpaRepository<CompositionTask, UUID> {
    List<CompositionTask> findByStatusOrderByCreatedAt(String status, Pageable pageable);
    List<CompositionTask> findByCompositionId(UUID compositionId);
}
