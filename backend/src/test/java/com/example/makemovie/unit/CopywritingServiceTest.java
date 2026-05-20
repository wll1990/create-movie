package com.example.makemovie.unit;

import com.example.makemovie.client.ImageGenClient;
import com.example.makemovie.client.LlmClient;
import com.example.makemovie.dto.response.CopywritingResponse;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Script;
import com.example.makemovie.enums.ProjectMode;
import com.example.makemovie.repository.CompositionRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.ScriptRepository;
import com.example.makemovie.service.CopywritingService;
import com.example.makemovie.service.ImageStorageService;
import com.example.makemovie.service.ProgressService;
import com.example.makemovie.service.PromptLoader;
import com.example.makemovie.service.WorkflowLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CopywritingServiceTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private ImageGenClient imageGenClient;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ScriptRepository scriptRepository;
    @Mock
    private CompositionRepository compositionRepository;
    @Mock
    private WorkflowLogService workflowLogService;
    @Mock
    private ProgressService progressService;
    @Mock
    private PromptLoader promptLoader;

    private CopywritingService service;
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(promptLoader.load(anyString(), anyMap())).thenReturn("copywriting prompt");
        service = new CopywritingService(llmClient, imageGenClient, imageStorageService,
                projectRepository, scriptRepository,
                compositionRepository, workflowLogService, progressService,
                promptLoader, new ObjectMapper());
    }

    @Test
    void generateCopywriting_shouldReturnFullCopywriting() {
        Project project = Project.builder()
                .id(PROJECT_ID).title("霸总的秘密").track("都市甜宠")
                .mode(ProjectMode.CREATION).build();

        Script script = Script.builder()
                .projectId(PROJECT_ID).title("霸总的秘密")
                .content(Map.of("scenes", List.of(
                        Map.of("summary", "女主在咖啡厅遇到霸总"),
                        Map.of("summary", "两人幸福在一起")
                )))
                .build();

        String llmResponse = """
                {
                    "title": "霸总的秘密，甜到窒息！",
                    "description": "咖啡厅打工妹意外邂逅霸道总裁，从此命运改变...",
                    "hashtags": ["都市甜宠", "霸道总裁", "甜剧推荐", "漫剧", "恋爱"],
                    "coverDescription": "男女主角对视的浪漫画面，暖色调"
                }
                """;

        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(script));
        when(compositionRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of());
        when(llmClient.generate(anyString())).thenReturn(llmResponse);

        CopywritingResponse result = service.generateCopywriting(PROJECT_ID);

        assertThat(result.getTitle()).isEqualTo("霸总的秘密，甜到窒息！");
        assertThat(result.getHashtags()).hasSize(5);
        assertThat(result.getDescription()).contains("咖啡厅");
        assertThat(result.getCoverDescription()).isNotEmpty();

        verify(projectRepository).save(any(Project.class));
        verify(progressService).refreshProgress(any(Project.class));
    }
}
