package com.example.makemovie.dto.response;

import com.example.makemovie.enums.WorkflowStep;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowLogResponse {
    private UUID id;
    private UUID projectId;
    private WorkflowStep step;
    private String status;
    private String prompt;
    private Map<String, Object> inputData;
    private Map<String, Object> outputData;
    private String errorMessage;
    private Integer llmResponseTimeMs;
    private Integer retryCount;
    private LocalDateTime createdAt;
}
