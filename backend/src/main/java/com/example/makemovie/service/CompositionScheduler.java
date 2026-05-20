package com.example.makemovie.service;

import com.example.makemovie.entity.CompositionTask;
import com.example.makemovie.repository.CompositionTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task that polls the composition_task queue and processes pending tasks.
 * Runs every few seconds, picks up to maxParallelTasks QUEUED items.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositionScheduler {

    private final CompositionTaskRepository taskRepository;
    private final VideoComposerService videoComposerService;

    @Value("${video.composition.max-parallel-tasks:2}")
    private int maxParallelTasks;

    @Scheduled(fixedDelayString = "${video.composition.poll-interval-ms:5000}")
    public void processQueue() {
        // Count currently processing tasks
        List<CompositionTask> processing = taskRepository
                .findByStatusOrderByCreatedAt("PROCESSING", Pageable.ofSize(maxParallelTasks + 1));
        int processingCount = processing.size();

        if (processingCount >= maxParallelTasks) {
            return; // Already at max parallelism
        }

        int available = maxParallelTasks - processingCount;
        List<CompositionTask> queued = taskRepository
                .findByStatusOrderByCreatedAt("QUEUED", Pageable.ofSize(available));

        for (CompositionTask task : queued) {
            log.info("Starting composition task: id={}", task.getId());
            videoComposerService.execute(task);
        }
    }
}
