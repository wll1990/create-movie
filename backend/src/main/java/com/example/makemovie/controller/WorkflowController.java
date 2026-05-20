package com.example.makemovie.controller;

import com.example.makemovie.service.WorkflowEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowEngine workflowEngine;

    @GetMapping("/api/workflow/definitions")
    public ResponseEntity<List<Map<String, Object>>> getDefinitions() {
        return ResponseEntity.ok(workflowEngine.getDefinitions());
    }

    @GetMapping("/api/workflow/projects/{projectId}/state")
    public ResponseEntity<Map<String, Object>> getProjectState(@PathVariable UUID projectId) {
        return ResponseEntity.ok(workflowEngine.getProjectState(projectId));
    }
}
