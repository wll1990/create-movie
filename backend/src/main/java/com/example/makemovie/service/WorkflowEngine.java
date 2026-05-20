package com.example.makemovie.service;

import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.WorkflowLog;
import com.example.makemovie.event.WorkflowEvent.*;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.WorkflowLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * Single source of truth for workflow state management.
 *
 * Services publish events → Engine listens → manages state in WorkflowLog + Project.progress
 */
@Slf4j
@Component
public class WorkflowEngine {

    private final WorkflowLogRepository workflowLogRepository;
    private final ProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Getter
    private List<Map<String, Object>> definitions = List.of();

    public WorkflowEngine(WorkflowLogRepository workflowLogRepository,
                          ProjectRepository projectRepository,
                          ObjectMapper objectMapper) {
        this.workflowLogRepository = workflowLogRepository;
        this.projectRepository = projectRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadDefinitions() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("workflow.yml")) {
            if (is == null) {
                log.error("workflow.yml not found!");
                return;
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps =
                    (List<Map<String, Object>>) ((Map<String, Object>) root.get("workflow")).get("steps");
            definitions = List.copyOf(steps);
            log.info("WorkflowEngine: loaded {} step definitions", definitions.size());
        } catch (Exception e) {
            log.error("Failed to load workflow.yml", e);
        }
    }

    // ============================================================
    // Event Handlers
    // ============================================================

    @EventListener
    @Transactional
    public void onStepStarted(StepStartedEvent event) {
        log.info("WorkflowEngine: step STARTED project={} step={}", event.getProjectId(), event.getStepKey());
        WorkflowLog wfLog = findOrCreateLog(event.getProjectId(), event.getStepKey());
        wfLog.setStatus("RUNNING");
        if (event.getPrompt() != null) wfLog.setPrompt(event.getPrompt());
        if (event.getInputData() != null) wfLog.setInputData(event.getInputData());
        workflowLogRepository.save(wfLog);
        refreshProjectProgress(event.getProjectId());
    }

    @EventListener
    @Transactional
    public void onStepCompleted(StepCompletedEvent event) {
        log.info("WorkflowEngine: step COMPLETED project={} step={} elapsed={}ms",
                event.getProjectId(), event.getStepKey(), event.getElapsedMs());
        WorkflowLog wfLog = findOrCreateLog(event.getProjectId(), event.getStepKey());
        wfLog.setStatus("COMPLETED");
        if (event.getOutputData() != null) wfLog.setOutputData(event.getOutputData());
        if (event.getPrompt() != null) wfLog.setPrompt(event.getPrompt());
        if (event.getElapsedMs() > 0) wfLog.setLlmResponseTimeMs((int) event.getElapsedMs());
        workflowLogRepository.save(wfLog);
        refreshProjectProgress(event.getProjectId());
    }

    @EventListener
    @Transactional
    public void onStepFailed(StepFailedEvent event) {
        log.error("WorkflowEngine: step FAILED project={} step={} error={}",
                event.getProjectId(), event.getStepKey(), event.getErrorMessage());
        WorkflowLog wfLog = findOrCreateLog(event.getProjectId(), event.getStepKey());
        wfLog.setStatus("FAILED");
        wfLog.setErrorMessage(event.getErrorMessage());
        wfLog.setRetryCount(wfLog.getRetryCount() + 1);
        workflowLogRepository.save(wfLog);
        refreshProjectProgress(event.getProjectId());
    }

    // ============================================================
    // Public API
    // ============================================================

    public Map<String, Object> getProjectState(UUID projectId) {
        List<WorkflowLog> logs = workflowLogRepository.findByProjectIdOrderByCreatedAt(projectId);
        Map<String, Object> state = new LinkedHashMap<>();

        int completed = 0;
        String currentStep = null;

        for (Map<String, Object> def : definitions) {
            String key = (String) def.get("key");
            WorkflowLog log = logs.stream()
                    .filter(l -> l.getStep() != null && l.getStep().name().equals(key))
                    .findFirst().orElse(null);

            Map<String, Object> stepState = new LinkedHashMap<>();
            if (log != null) {
                stepState.put("status", log.getStatus());
                if ("COMPLETED".equals(log.getStatus())) {
                    stepState.put("completedAt", log.getCreatedAt().toString());
                    completed++;
                }
                if ("RUNNING".equals(log.getStatus())) {
                    currentStep = key;
                }
            } else {
                stepState.put("status", "PENDING");
            }
            state.put(key, stepState);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentStep", currentStep);
        result.put("totalSteps", definitions.size());
        result.put("completedSteps", completed);
        result.put("overallProgress", completed * 100 / Math.max(definitions.size(), 1));
        result.put("steps", state);
        return result;
    }

    public void resetDownstream(UUID projectId, String fromStepKey) {
        boolean found = false;
        for (Map<String, Object> def : definitions) {
            String key = (String) def.get("key");
            if (key.equals(fromStepKey)) { found = true; continue; }
            if (found) {
                com.example.makemovie.enums.WorkflowStep s =
                        com.example.makemovie.enums.WorkflowStep.valueOf(key);
                workflowLogRepository.findByProjectIdAndStep(projectId, s)
                        .ifPresent(wfLog -> {
                            wfLog.setStatus("PENDING");
                            wfLog.setOutputData(Map.of());
                            wfLog.setPrompt(null);
                            wfLog.setErrorMessage(null);
                            wfLog.setLlmResponseTimeMs(null);
                            workflowLogRepository.save(wfLog);
                        });
            }
        }
    }

    // ============================================================
    // Internal
    // ============================================================

    private WorkflowLog findOrCreateLog(UUID projectId, String stepKey) {
        com.example.makemovie.enums.WorkflowStep stepEnum =
                com.example.makemovie.enums.WorkflowStep.valueOf(stepKey);
        return workflowLogRepository.findFirstByProjectIdAndStepOrderByCreatedAtDesc(projectId, stepEnum)
                .orElseGet(() -> {
                    WorkflowLog wfLog = WorkflowLog.builder()
                            .projectId(projectId)
                            .step(stepEnum)
                            .status("PENDING")
                            .build();
                    return workflowLogRepository.save(wfLog);
                });
    }

    private void refreshProjectProgress(UUID projectId) {
        projectRepository.findById(projectId).ifPresent(project -> {
            Map<String, Object> state = getProjectState(projectId);
            project.setProgress(state);
            String status = "DRAFT";
            if (state.get("currentStep") != null) status = "PROCESSING";
            if ((int) state.get("completedSteps") == definitions.size()) status = "COMPLETED";
            project.setStatus(status);
            projectRepository.save(project);
        });
    }
}
