package com.example.makemovie.service;

import com.example.makemovie.dto.request.CreateProjectRequest;
import com.example.makemovie.dto.response.ProjectResponse;
import com.example.makemovie.entity.Episode;
import com.example.makemovie.entity.Project;
import com.example.makemovie.enums.ProjectMode;
import com.example.makemovie.event.WorkflowEvent.*;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final EpisodeService episodeService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        Project project = Project.builder()
                .title(request.getTitle())
                .track(request.getTrack())
                .theme(request.getTheme())
                .mode(request.getMode())
                .sourceVideoGeneId(request.getSourceVideoGeneId() != null ?
                        UUID.fromString(request.getSourceVideoGeneId()) : null)
                .creationTemplateId(request.getCreationTemplateId() != null ?
                        UUID.fromString(request.getCreationTemplateId()) : null)
                .status("DRAFT")
                .build();

        project = projectRepository.save(project);

        // 创建第1集
        Episode episode = episodeService.createEpisode(project.getId(), 1, request.getTitle());

        // Engine handles state: TOPIC_DESIGN auto-completed
        Map<String, Object> topicData = new LinkedHashMap<>();
        topicData.put("title", request.getTitle());
        topicData.put("track", request.getTrack());
        topicData.put("theme", request.getTheme());
        topicData.put("mode", request.getMode() != null ? request.getMode().name() : "CREATION");
        eventPublisher.publishEvent(new StepCompletedEvent(
                this, project.getId(), "TOPIC_DESIGN", topicData, 0, null));

        log.info("Project created: id={}, title={}", project.getId(), project.getTitle());
        return toResponse(project);
    }

    public ProjectResponse getProject(UUID id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "项目不存在: " + id));
        return toResponse(project);
    }

    public Page<ProjectResponse> listProjects(String status, ProjectMode mode,
                                               String track, Pageable pageable) {
        Page<Project> page;
        if (status != null) {
            page = projectRepository.findByStatus(status, pageable);
        } else if (mode != null) {
            page = projectRepository.findByMode(mode, pageable);
        } else if (track != null) {
            page = projectRepository.findByTrack(track, pageable);
        } else {
            page = projectRepository.findAll(pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional
    public void deleteProject(UUID id) {
        if (!projectRepository.existsById(id)) {
            throw new BusinessException("PROJECT_NOT_FOUND", "项目不存在: " + id);
        }
        projectRepository.deleteById(id);
        log.info("Project deleted: id={}", id);
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .track(project.getTrack())
                .theme(project.getTheme())
                .mode(project.getMode())
                .status(project.getStatus())
                .sourceVideoGeneId(project.getSourceVideoGeneId())
                .creationTemplateId(project.getCreationTemplateId())
                .progress(project.getProgress())
                .episodeCount(episodeService.getEpisodeCount(project.getId()))
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
