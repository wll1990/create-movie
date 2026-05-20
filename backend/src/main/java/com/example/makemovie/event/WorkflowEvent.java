package com.example.makemovie.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Base class for workflow events.
 * Services publish these — the WorkflowEngine listens and manages state.
 */
@Getter
public abstract class WorkflowEvent extends ApplicationEvent {
    private final UUID projectId;
    private final String stepKey;

    protected WorkflowEvent(Object source, UUID projectId, String stepKey) {
        super(source);
        this.projectId = projectId;
        this.stepKey = stepKey;
    }

    @Getter
    public static class StepStartedEvent extends WorkflowEvent {
        private final String prompt;
        private final Map<String, Object> inputData;

        public StepStartedEvent(Object source, UUID projectId, String stepKey,
                                String prompt, Map<String, Object> inputData) {
            super(source, projectId, stepKey);
            this.prompt = prompt;
            this.inputData = inputData;
        }
    }

    @Getter
    public static class StepCompletedEvent extends WorkflowEvent {
        private final Map<String, Object> outputData;
        private final long elapsedMs;
        private final String prompt;

        public StepCompletedEvent(Object source, UUID projectId, String stepKey,
                                  Map<String, Object> outputData, long elapsedMs, String prompt) {
            super(source, projectId, stepKey);
            this.outputData = outputData;
            this.elapsedMs = elapsedMs;
            this.prompt = prompt;
        }
    }

    @Getter
    public static class StepFailedEvent extends WorkflowEvent {
        private final String errorMessage;

        public StepFailedEvent(Object source, UUID projectId, String stepKey,
                               String errorMessage) {
            super(source, projectId, stepKey);
            this.errorMessage = errorMessage;
        }
    }
}
