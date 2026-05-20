package com.example.makemovie.service;

import com.example.makemovie.dto.response.WorkflowLogResponse;
import com.example.makemovie.entity.WorkflowLog;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.WorkflowLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowLogService {

    private final WorkflowLogRepository workflowLogRepository;

    @Transactional
    public WorkflowLog initStep(UUID projectId, WorkflowStep step) {
        WorkflowLog wfLog = WorkflowLog.builder()
                .projectId(projectId)
                .step(step)
                .status(StepStatus.PENDING.name())
                .build();
        return workflowLogRepository.save(wfLog);
    }

    @Transactional
    public WorkflowLog initStep(UUID projectId, UUID episodeId, WorkflowStep step) {
        WorkflowLog wfLog = WorkflowLog.builder()
                .projectId(projectId)
                .episodeId(episodeId)
                .step(step)
                .status(StepStatus.PENDING.name())
                .build();
        return workflowLogRepository.save(wfLog);
    }

    @Transactional
    public void updateStatus(UUID projectId, WorkflowStep step, StepStatus status,
                              Map<String, Object> inputData,
                              Map<String, Object> outputData,
                              long responseTimeMs) {
        updateStatus(projectId, step, status, inputData, outputData, responseTimeMs, null);
    }

    @Transactional
    public void updateStatus(UUID projectId, WorkflowStep step, StepStatus status,
                              Map<String, Object> inputData,
                              Map<String, Object> outputData,
                              long responseTimeMs,
                              String prompt) {
        WorkflowLog wfLog = workflowLogRepository.findFirstByProjectIdAndStepOrderByCreatedAtDesc(projectId, step)
                .orElseGet(() -> {
                    WorkflowLog newLog = WorkflowLog.builder()
                            .projectId(projectId)
                            .step(step)
                            .status("PENDING")
                            .build();
                    return workflowLogRepository.save(newLog);
                });

        wfLog.setStatus(status.name());
        if (inputData != null) wfLog.setInputData(inputData);
        if (outputData != null) wfLog.setOutputData(outputData);
        if (responseTimeMs > 0) wfLog.setLlmResponseTimeMs((int) responseTimeMs);
        if (prompt != null) wfLog.setPrompt(prompt);

        workflowLogRepository.save(wfLog);
        log.info("WorkflowLog updated: project={}, step={}, status={}", projectId, step.getDisplayName(), status);
    }

    @Transactional
    public void resetDownstreamSteps(UUID projectId, WorkflowStep fromStep) {
        WorkflowStep[] allSteps = WorkflowStep.values();
        boolean found = false;
        for (WorkflowStep step : allSteps) {
            if (step == fromStep) {
                found = true;
                continue;
            }
            if (found) {
                workflowLogRepository.findFirstByProjectIdAndStepOrderByCreatedAtDesc(projectId, step)
                        .ifPresent(wfLog -> {
                            wfLog.setStatus(StepStatus.PENDING.name());
                            wfLog.setOutputData(Map.of());
                            wfLog.setPrompt(null);
                            wfLog.setErrorMessage(null);
                            wfLog.setLlmResponseTimeMs(null);
                            workflowLogRepository.save(wfLog);
                            log.info("WorkflowLog reset: project={}, step={} → PENDING", projectId, step.getDisplayName());
                        });
            }
        }
    }

    @Transactional
    public void markFailed(UUID projectId, WorkflowStep step, String errorMessage) {
        WorkflowLog wfLog = workflowLogRepository.findFirstByProjectIdAndStepOrderByCreatedAtDesc(projectId, step)
                .orElseGet(() -> {
                    WorkflowLog newLog = WorkflowLog.builder()
                            .projectId(projectId)
                            .step(step)
                            .status("PENDING")
                            .build();
                    return workflowLogRepository.save(newLog);
                });

        wfLog.setStatus(StepStatus.FAILED.name());
        wfLog.setErrorMessage(errorMessage);
        wfLog.setRetryCount(wfLog.getRetryCount() + 1);
        workflowLogRepository.save(wfLog);
        log.error("WorkflowLog FAILED: project={}, step={}, error={}", projectId, step.getDisplayName(), errorMessage);
    }

    public List<WorkflowLogResponse> getProjectLogs(UUID projectId) {
        return workflowLogRepository.findByProjectIdOrderByCreatedAt(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkflowLogResponse getStepLog(UUID projectId, WorkflowStep step) {
        WorkflowLog wfLog = workflowLogRepository.findFirstByProjectIdAndStepOrderByCreatedAtDesc(projectId, step)
                .orElseGet(() -> {
                    WorkflowLog newLog = WorkflowLog.builder()
                            .projectId(projectId)
                            .step(step)
                            .status("PENDING")
                            .build();
                    return workflowLogRepository.save(newLog);
                });
        return toResponse(wfLog);
    }

    private WorkflowLogResponse toResponse(WorkflowLog wfLog) {
        return WorkflowLogResponse.builder()
                .id(wfLog.getId())
                .projectId(wfLog.getProjectId())
                .step(wfLog.getStep())
                .status(wfLog.getStatus())
                .prompt(wfLog.getPrompt())
                .inputData(wfLog.getInputData())
                .outputData(wfLog.getOutputData())
                .errorMessage(wfLog.getErrorMessage())
                .llmResponseTimeMs(wfLog.getLlmResponseTimeMs())
                .retryCount(wfLog.getRetryCount())
                .createdAt(wfLog.getCreatedAt())
                .build();
    }
}
