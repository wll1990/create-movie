package com.example.makemovie.unit;

import com.example.makemovie.client.LlmClient;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Script;
import com.example.makemovie.enums.ProjectMode;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.ClipTaskRepository;
import com.example.makemovie.repository.CompositionRepository;
import com.example.makemovie.repository.CompositionTaskRepository;
import com.example.makemovie.repository.EpisodeRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.ScriptRepository;
import com.example.makemovie.repository.StoryboardFrameRepository;
import com.example.makemovie.repository.StoryboardRepository;
import com.example.makemovie.service.ScriptService;
import com.example.makemovie.service.WorkflowEngine;
import com.example.makemovie.validation.JsonSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScriptServiceTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private ScriptRepository scriptRepository;
    @Mock
    private EpisodeRepository episodeRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private StoryboardRepository storyboardRepository;
    @Mock
    private StoryboardFrameRepository storyboardFrameRepository;
    @Mock
    private CompositionRepository compositionRepository;
    @Mock
    private CompositionTaskRepository compositionTaskRepository;
    @Mock
    private ClipTaskRepository clipTaskRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private com.example.makemovie.service.WorkflowEngine workflowEngine;
    @Mock
    private com.example.makemovie.service.PromptLoader promptLoader;
    @Mock
    private JsonSchemaValidator schemaValidator;

    private ObjectMapper objectMapper;
    private ScriptService scriptService;

    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(promptLoader.load(anyString(), anyMap())).thenReturn("script prompt");
        lenient().when(characterRepository.findByProjectId(any())).thenReturn(List.of());
        lenient().when(episodeRepository.findByProjectIdOrderByEpisodeNumber(any())).thenReturn(List.of());
        scriptService = new ScriptService(
                llmClient, scriptRepository, episodeRepository, projectRepository,
                characterRepository, storyboardRepository, storyboardFrameRepository,
                compositionRepository, compositionTaskRepository, clipTaskRepository,
                eventPublisher, workflowEngine,
                promptLoader, schemaValidator, objectMapper);
    }

    @Test
    void generateScript_shouldReturnScript_whenLlmReturnsValidJson() {
        // Given
        String llmResponse = """
                {
                    "title": "霸总的秘密",
                    "track": "都市甜宠",
                    "duration": 45,
                    "scenes": [
                        {
                            "sceneNumber": 1,
                            "location": "咖啡厅",
                            "timeOfDay": "下午",
                            "summary": "女主在咖啡厅打工",
                            "dialogues": [
                                {
                                    "characterName": "女主",
                                    "text": "先生，您的咖啡...",
                                    "emotion": "surprised",
                                    "durationEstimate": 2.5
                                }
                            ],
                            "durationEstimate": 15
                        }
                    ]
                }
                """;

        when(schemaValidator.validate(anyString(), anyString())).thenReturn(Set.of());
        when(llmClient.generate(anyString())).thenReturn(llmResponse);
        when(scriptRepository.save(any(Script.class))).thenAnswer(inv -> {
            Script s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(Project.builder().id(PROJECT_ID).title("Test").mode(ProjectMode.CREATION).build()));
        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        // When
        var result = scriptService.generateScript(PROJECT_ID, "都市甜宠", "霸总", 45, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("霸总的秘密");
        assertThat(result.getTrack()).isEqualTo("都市甜宠");

        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void generateScript_shouldRetry_whenValidationFails() {
        // Given
        String badResponse = "{\"title\": \"missing scenes\"}";
        String goodResponse = """
                {
                    "title": "Fixed",
                    "track": "都市甜宠",
                    "duration": 45,
                    "scenes": [{"sceneNumber":1, "location":"家", "dialogues":[{"characterName":"A","text":"Hi","emotion":"neutral"}]}]
                }
                """;

        when(schemaValidator.validate(anyString(), eq(badResponse)))
                .thenReturn(Set.of(ValidationMessage.builder().message("missing scenes").build()));
        when(schemaValidator.validate(anyString(), eq(goodResponse)))
                .thenReturn(Set.of());
        when(llmClient.generate(anyString()))
                .thenReturn(badResponse)
                .thenReturn(goodResponse);
        when(scriptRepository.save(any(Script.class))).thenAnswer(inv -> {
            Script s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(Project.builder().id(PROJECT_ID).title("Test").mode(ProjectMode.CREATION).build()));
        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        // When
        var result = scriptService.generateScript(PROJECT_ID, "甜宠", "test", 30, null);

        // Then
        assertThat(result.getTitle()).isEqualTo("Fixed");
        verify(llmClient, times(2)).generate(anyString());
    }

    @Test
    void generateScript_shouldThrowBusinessException_whenAllRetriesExhausted() {
        // Given
        when(llmClient.generate(anyString()))
                .thenThrow(new RuntimeException("LLM timeout"));

        // When + Then
        assertThatThrownBy(() ->
                scriptService.generateScript(PROJECT_ID, "甜宠", "test", 30, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("剧本生成失败");

        verify(eventPublisher).publishEvent(any());
    }

    @Test
    void getScript_shouldReturnScript_whenExists() {
        // Given
        Script script = Script.builder()
                .id(UUID.randomUUID())
                .projectId(PROJECT_ID)
                .title("测试剧本")
                .content(Map.of("scenes", List.of()))
                .build();
        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(script));

        // When
        var result = scriptService.getScript(PROJECT_ID);

        // Then
        assertThat(result.getTitle()).isEqualTo("测试剧本");
    }

    @Test
    void getScript_shouldThrow_whenNotFound() {
        // Given
        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        // When + Then
        assertThatThrownBy(() -> scriptService.getScript(PROJECT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未生成剧本");
    }

    @Test
    void updateScript_shouldIncrementVersion() {
        // Given
        Script script = Script.builder()
                .id(UUID.randomUUID())
                .projectId(PROJECT_ID)
                .title("Old Title")
                .content(Map.of("scenes", List.of()))
                .version(1)
                .build();
        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(script));
        when(scriptRepository.save(any(Script.class))).thenReturn(script);

        // When
        var result = scriptService.updateScript(PROJECT_ID, "New Title",
                Map.of("scenes", List.of(Map.of("sceneNumber", 1))));

        // Then
        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getVersion()).isEqualTo(2);
    }
}
