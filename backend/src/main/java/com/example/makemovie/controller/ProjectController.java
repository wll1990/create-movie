package com.example.makemovie.controller;

import com.example.makemovie.dto.request.CreateProjectRequest;
import com.example.makemovie.dto.response.ProjectResponse;
import com.example.makemovie.enums.ProjectMode;
import com.example.makemovie.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(projectService.createProject(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    @GetMapping
    public ResponseEntity<Page<ProjectResponse>> listProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) ProjectMode mode,
            @RequestParam(required = false) String track,
            Pageable pageable) {
        return ResponseEntity.ok(projectService.listProjects(status, mode, track, pageable));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok(Map.of("message", "项目已删除", "id", id));
    }
}
