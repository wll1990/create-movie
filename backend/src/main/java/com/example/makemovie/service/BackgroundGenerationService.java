package com.example.makemovie.service;

import com.example.makemovie.client.ImageGenClient;
import com.example.makemovie.entity.ImageGenTask;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.StoryboardFrame;
import com.example.makemovie.repository.ImageGenTaskRepository;
import com.example.makemovie.repository.StoryboardFrameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackgroundGenerationService {

    private final ImageGenClient imageGenClient;
    private final ImageGenTaskRepository taskRepository;
    private final ImageStorageService imageStorageService;
    private final StoryboardFrameRepository frameRepository;

    /**
     * Generate background images for unique scene descriptions asynchronously.
     * Uses the backgroundGenExecutor thread pool.
     */
    @Async("backgroundGenExecutor")
    public void generateSceneBackgrounds(Project project, UUID storyboardId) {
        try {
            List<StoryboardFrame> frames = frameRepository
                    .findByStoryboardIdOrderByFrameNumber(storyboardId);

            // Collect unique background descriptions
            Map<String, List<StoryboardFrame>> sceneGroups = new LinkedHashMap<>();
            for (StoryboardFrame frame : frames) {
                String key = (frame.getBgDescription() != null ? frame.getBgDescription() : "")
                        + "|" + frame.getShotType();
                sceneGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(frame);
            }

            log.info("Generating backgrounds for {} unique scenes in storyboard {}",
                    sceneGroups.size(), storyboardId);

            for (Map.Entry<String, List<StoryboardFrame>> entry : sceneGroups.entrySet()) {
                StoryboardFrame firstFrame = entry.getValue().get(0);
                if (firstFrame.getBgDescription() == null
                        || firstFrame.getBgDescription().isBlank()) continue;

                // Skip if already has a background image
                if (firstFrame.getBgImageUrl() != null
                        && !firstFrame.getBgImageUrl().isBlank()) continue;

                try {
                    String desc = firstFrame.getBgDescription();
                    String taskId = imageGenClient.generateSceneBackground(
                            desc, firstFrame.getShotType(), "白天", project.getTrack());
                    if (taskId != null) {
                        // Store frame IDs in callback key so poller can update them
                        List<String> frameIds = entry.getValue().stream()
                                .map(f -> f.getId().toString()).toList();
                        ImageGenTask task = ImageGenTask.builder()
                                .projectId(project.getId())
                                .taskType("BACKGROUND")
                                .externalTaskId(taskId)
                                .callbackKey(String.join(",", frameIds))
                                .prompt(desc)
                                .status("PENDING").build();
                        taskRepository.save(task);
                        // Picked up by ImageGenPoller from DB
                    }
                } catch (Exception e) {
                    log.warn("Background task submission failed: {} — {}",
                            firstFrame.getBgDescription(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Background generation batch failed for storyboard {}: {}",
                    storyboardId, e.getMessage());
        }
    }
}
