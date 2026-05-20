package com.example.makemovie.service;

import com.example.makemovie.client.ImageGenClient;
import com.example.makemovie.entity.ImageGenTask;
import com.example.makemovie.entity.StoryboardFrame;
import com.example.makemovie.event.ImageTaskCompletedEvent;
import com.example.makemovie.repository.ImageGenTaskRepository;
import com.example.makemovie.repository.StoryboardFrameRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled poller for image generation tasks.
 *
 * Uses a memory queue (ConcurrentLinkedQueue) as primary work source,
 * falling back to DB queries when the queue is empty.
 * On restart, loads unfinished tasks from DB back into memory.
 *
 * Design:
 * - submit: DB INSERT + queue.add (dual write)
 * - poll: drain queue first, DB fallback second
 * - rebuild: @PostConstruct load PENDING/PROCESSING from DB
 * - complete: download image → store MinIO+OSS → update Character → mark DB COMPLETED
 */
@Slf4j
@Service
public class ImageGenPoller {

    private final ImageGenClient imageGenClient;
    private final ImageGenTaskRepository taskRepository;
    private final StoryboardFrameRepository frameRepository;
    private final ImageStorageService imageStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor pollExecutor;

    @Value("${image-gen.max-parallel-poll:5}")
    private int maxParallelPoll;

    public ImageGenPoller(ImageGenClient imageGenClient,
                          ImageGenTaskRepository taskRepository,
                          StoryboardFrameRepository frameRepository,
                          ImageStorageService imageStorageService,
                          ApplicationEventPublisher eventPublisher,
                          @Qualifier("imageGenPollExecutor") Executor pollExecutor) {
        this.imageGenClient = imageGenClient;
        this.taskRepository = taskRepository;
        this.frameRepository = frameRepository;
        this.imageStorageService = imageStorageService;
        this.eventPublisher = eventPublisher;
        this.pollExecutor = pollExecutor;
    }

    private final ConcurrentLinkedQueue<UUID> taskQueue = new ConcurrentLinkedQueue<>();

    @PostConstruct
    void recover() {
        if (!imageGenClient.isConfigured()) {
            log.info("ImageGenPoller: image gen not configured, skipping");
            return;
        }
        List<ImageGenTask> pending = taskRepository
                .findByStatusInOrderByCreatedAt(List.of("PENDING", "PROCESSING"));
        for (ImageGenTask t : pending) {
            taskQueue.add(t.getId());
        }
        log.info("ImageGenPoller: recovered {} pending tasks from DB", pending.size());
    }

    /**
     * Enqueue a task for polling. Called by CharacterImageService after saving the task.
     */
    public void enqueue(UUID taskId) {
        taskQueue.add(taskId);
    }

    @Scheduled(fixedDelayString = "${image-gen.poll-interval-ms:3000}")
    public void tick() {
        if (!imageGenClient.isConfigured()) return;

        // Collect up to maxParallelPoll tasks
        List<UUID> batch = new ArrayList<>();
        for (int i = 0; i < maxParallelPoll; i++) {
            UUID id = taskQueue.poll();
            if (id == null) break;
            batch.add(id);
        }

        // If memory queue is empty, pull from DB
        if (batch.isEmpty()) {
            List<ImageGenTask> dbTasks = taskRepository
                    .findByStatusInAndPollCountLessThanOrderByCreatedAt(
                            List.of("PENDING", "PROCESSING"), 90);
            for (ImageGenTask t : dbTasks) {
                batch.add(t.getId());
            }
            if (!batch.isEmpty()) {
                log.debug("ImageGenPoller: loaded {} tasks from DB", batch.size());
            }
        }

        if (batch.isEmpty()) return;

        log.info("ImageGenPoller: polling {} tasks (queue remaining: {})", batch.size(), taskQueue.size());

        // Poll concurrently
        List<CompletableFuture<Void>> futures = batch.stream()
                .map(taskId -> CompletableFuture.runAsync(() -> processTask(taskId), pollExecutor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("ImageGenPoller: batch poll timeout or interrupted: {}", e.getMessage());
        }
    }

    private void processTask(UUID taskId) {
        Optional<ImageGenTask> opt = taskRepository.findById(taskId);
        if (opt.isEmpty()) return;

        ImageGenTask task = opt.get();
        if ("COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) return;

        task.setStatus("PROCESSING");
        task.setPollCount(task.getPollCount() + 1);
        taskRepository.save(task);

        ImageGenClient.PollResult result = imageGenClient.pollTask(task.getExternalTaskId());

        switch (result.getStatus()) {
            case "COMPLETED" -> handleCompleted(task, result.getResultUrl());
            case "FAILED" -> handleFailed(task, result.getErrorMessage());
            case "PENDING" -> {
                // Re-enqueue for next poll cycle
                task.setStatus("PENDING");
                taskRepository.save(task);
                taskQueue.add(taskId);
            }
        }
    }

    private void handleCompleted(ImageGenTask task, String resultUrl) {
        log.info("ImageGenPoller: task {} ({}) completed", task.getId(), task.getTaskType());

        if (resultUrl == null || resultUrl.isBlank()) {
            handleFailed(task, "Empty result URL");
            return;
        }

        // Download and persist
        byte[] imageBytes = downloadBytes(resultUrl);
        String subPath = buildStoragePath(task);
        String persistentUrl = imageStorageService.storeProjectFile(
                task.getProjectId(), subPath, imageBytes, "image/png");

        task.setStatus("COMPLETED");
        task.setResultUrl(persistentUrl != null ? persistentUrl : resultUrl);
        taskRepository.save(task);

        // Publish event — CharacterImageService listens and handles
        if ("BACKGROUND".equals(task.getTaskType())) {
            handleBackgroundCompleted(task, persistentUrl != null ? persistentUrl : resultUrl);
        } else {
            eventPublisher.publishEvent(new ImageTaskCompletedEvent(
                    this, task, persistentUrl != null ? persistentUrl : resultUrl));
        }
    }

    private void handleFailed(ImageGenTask task, String errorMsg) {
        log.warn("ImageGenPoller: task {} ({}) FAILED: {}", task.getId(), task.getTaskType(), errorMsg);
        task.setStatus("FAILED");
        task.setErrorMessage(errorMsg != null ? errorMsg : "Unknown error");
        taskRepository.save(task);

        eventPublisher.publishEvent(new ImageTaskCompletedEvent(this, task, null));
    }

    private String buildStoragePath(ImageGenTask task) {
        return switch (task.getTaskType()) {
            case "PORTRAIT" -> String.format("02-characters/%s/candidates/%s.png",
                    sanitize(task.getCallbackKey()), task.getId().toString().substring(0, 8));
            case "THREEVIEW" -> String.format("02-characters/%s/threeview.png",
                    sanitize(task.getCallbackKey()));
            case "EXPRESSION" -> String.format("02-characters/%s/expressions/%s.png",
                    sanitize(task.getCallbackKey()), task.getId().toString().substring(0, 8));
            case "BACKGROUND" -> String.format("03-storyboard/backgrounds/%s.png",
                    task.getId().toString().substring(0, 8));
            default -> String.format("images/%s.png", task.getId().toString().substring(0, 8));
        };
    }

    private byte[] downloadBytes(String url) {
        if (url == null || url.isBlank()) return new byte[0];
        try (java.io.InputStream is = java.net.URI.create(url).toURL().openStream()) {
            return is.readAllBytes();
        } catch (Exception e) {
            log.warn("ImageGenPoller: download failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    private void handleBackgroundCompleted(ImageGenTask task, String persistentUrl) {
        String callbackKey = task.getCallbackKey();
        if (callbackKey == null || callbackKey.isBlank()) return;
        String[] frameIds = callbackKey.split(",");
        for (String fid : frameIds) {
            try {
                UUID frameId = UUID.fromString(fid.trim());
                frameRepository.findById(frameId).ifPresent(frame -> {
                    frame.setBgImageUrl(persistentUrl);
                    frameRepository.save(frame);
                });
            } catch (Exception e) {
                log.warn("Failed to update frame {} background: {}", fid, e.getMessage());
            }
        }
        log.info("Background applied to {} frames for task {}", frameIds.length, task.getId());
    }

    private String sanitize(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5]", "_");
    }
}
