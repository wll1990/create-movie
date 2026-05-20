package com.example.makemovie.controller;

import com.example.makemovie.dto.response.WorkflowLogResponse;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.service.WorkflowLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflow/logs")
@RequiredArgsConstructor
public class WorkflowLogController {

    private final WorkflowLogService workflowLogService;

    @GetMapping("/{projectId}")
    public ResponseEntity<List<WorkflowLogResponse>> getProjectLogs(@PathVariable UUID projectId) {
        return ResponseEntity.ok(workflowLogService.getProjectLogs(projectId));
    }

    @GetMapping("/{projectId}/{step}")
    public ResponseEntity<WorkflowLogResponse> getStepLog(@PathVariable UUID projectId,
                                                           @PathVariable WorkflowStep step) {
        return ResponseEntity.ok(workflowLogService.getStepLog(projectId, step));
    }
}
