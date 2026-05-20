package com.example.makemovie.repository;

import com.example.makemovie.entity.ImageGenTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImageGenTaskRepository extends JpaRepository<ImageGenTask, UUID> {

    List<ImageGenTask> findByStatusInOrderByCreatedAt(List<String> statuses);

    List<ImageGenTask> findByStatusInAndPollCountLessThanOrderByCreatedAt(
            List<String> statuses, int maxPolls);

    List<ImageGenTask> findByProjectIdOrderByCreatedAt(UUID projectId);
}
