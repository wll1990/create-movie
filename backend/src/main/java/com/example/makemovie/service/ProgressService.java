package com.example.makemovie.service;

import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.WorkflowLog;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.WorkflowLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final WorkflowLogRepository workflowLogRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public void refreshProgress(Project project) {
        List<WorkflowLog> logs = workflowLogRepository
                .findByProjectIdOrderByCreatedAt(project.getId());

        Map<String, Object> progress = new LinkedHashMap<>();
        Map<String, Object> steps = new LinkedHashMap<>();

        int completed = 0;
        String currentStep = null;

        for (WorkflowStep step : WorkflowStep.values()) {
            String stepName = step.name();
            WorkflowLog log = logs.stream()
                    .filter(l -> l.getStep() == step)
                    .findFirst()
                    .orElse(null);

            Map<String, Object> stepInfo = new LinkedHashMap<>();
            if (log != null) {
                stepInfo.put("status", log.getStatus());
                if ("COMPLETED".equals(log.getStatus())) {
                    stepInfo.put("completedAt", log.getCreatedAt().toString());
                    completed++;
                }
                if ("RUNNING".equals(log.getStatus())) {
                    currentStep = stepName;
                }
            } else {
                stepInfo.put("status", "PENDING");
            }
            steps.put(stepName, stepInfo);
        }

        progress.put("currentStep", currentStep);
        progress.put("totalSteps", WorkflowStep.values().length);
        progress.put("completedSteps", completed);
        progress.put("overallProgress", completed * 100 / WorkflowStep.values().length);
        progress.put("steps", steps);

        project.setProgress(progress);
        if (currentStep != null) {
            project.setStatus("PROCESSING");
        }
        if (completed == WorkflowStep.values().length) {
            project.setStatus("COMPLETED");
        }
        projectRepository.save(project);
    }
}
