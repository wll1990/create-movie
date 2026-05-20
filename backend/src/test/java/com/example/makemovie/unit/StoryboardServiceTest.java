package com.example.makemovie.unit;

import com.example.makemovie.client.LlmClient;
import com.example.makemovie.dto.response.StoryboardResponse;
import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Scene;
import com.example.makemovie.entity.Script;
import com.example.makemovie.entity.Storyboard;
import com.example.makemovie.enums.ProjectMode;
import com.example.makemovie.repository.*;
import com.example.makemovie.service.ProgressService;
import com.example.makemovie.service.StoryboardService;
import com.example.makemovie.service.WorkflowLogService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryboardServiceTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private StoryboardRepository storyboardRepository;
    @Mock
    private StoryboardFrameRepository frameRepository;
    @Mock
    private ScriptRepository scriptRepository;
    @Mock
    private SceneRepository sceneRepository;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkflowLogService workflowLogService;
    @Mock
    private ProgressService progressService;
    @Mock
    private com.example.makemovie.service.PromptLoader promptLoader;
    @Mock
    private JsonSchemaValidator schemaValidator;
    @Mock
    private com.example.makemovie.service.BackgroundGenerationService backgroundGenService;
    @Mock
    private com.example.makemovie.service.VideoGenPromptBuilder videoGenPromptBuilder;
    @Mock
    private com.example.makemovie.service.ImageStorageService imageStorageService;
    @Mock
    private WorkflowLogRepository workflowLogRepository;

    private StoryboardService storyboardService;
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID SCRIPT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(promptLoader.load(anyString(), anyMap())).thenReturn("storyboard prompt");
        storyboardService = new StoryboardService(
                llmClient,
                storyboardRepository, frameRepository,
                scriptRepository, sceneRepository, characterRepository,
                projectRepository, workflowLogService, progressService,
                promptLoader, schemaValidator, new ObjectMapper(),
                backgroundGenService, videoGenPromptBuilder, imageStorageService,
                workflowLogRepository);
    }

    @Test
    void generateStoryboard_shouldReturnStoryboard_whenLlmReturnsValidJson() {
        // Given
        Script script = Script.builder()
                .id(SCRIPT_ID)
                .projectId(PROJECT_ID)
                .content(Map.of("scenes", List.of(
                        Map.of("sceneNumber", 1, "summary", "开场", "dialogues", List.of(
                                Map.of("characterName", "女主", "text", "你好")
                        ))
                )))
                .build();

        List<Character> characters = List.of(
                Character.builder().id(UUID.randomUUID()).projectId(PROJECT_ID)
                        .name("女主").role("PROTAGONIST").personality("温柔").build()
        );

        String llmResponse = """
                {
                    "totalFrames": 2,
                    "frames": [
                        {
                            "frameNumber": 1,
                            "sceneNumber": 1,
                            "shotType": "CU",
                            "cameraAngle": "平视",
                            "bgDescription": "咖啡厅内部",
                            "characters": [{"characterName":"女主","expression":"happy","position":{"x":0.5,"y":0.4},"scale":1.0,"layer":0}],
                            "dialogueText": "你好",
                            "durationSec": 3.0,
                            "transition": "cut"
                        },
                        {
                            "frameNumber": 2,
                            "sceneNumber": 1,
                            "shotType": "MS",
                            "cameraAngle": "平视",
                            "bgDescription": "咖啡厅全景",
                            "characters": [],
                            "dialogueText": "",
                            "durationSec": 2.0,
                            "transition": "fade"
                        }
                    ]
                }
                """;

        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(script));
        when(characterRepository.findByProjectId(PROJECT_ID)).thenReturn(characters);
        when(schemaValidator.validate(anyString(), anyString())).thenReturn(Set.of());
        when(llmClient.generate(anyString())).thenReturn(llmResponse);
        when(storyboardRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(storyboardRepository.save(any(Storyboard.class))).thenAnswer(inv -> {
            Storyboard sb = inv.getArgument(0);
            sb.setId(UUID.randomUUID());
            return sb;
        });
        when(sceneRepository.findByScriptIdOrderBySceneNumber(SCRIPT_ID))
                .thenReturn(List.of(Scene.builder()
                        .id(UUID.randomUUID()).scriptId(SCRIPT_ID)
                        .sceneNumber(1).build()));
        when(frameRepository.save(any())).thenAnswer(inv -> {
            com.example.makemovie.entity.StoryboardFrame f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(Project.builder().id(PROJECT_ID).title("Test").mode(ProjectMode.CREATION).build()));

        // When
        StoryboardResponse result = storyboardService.generateStoryboard(PROJECT_ID);

        // Then
        assertThat(result.getTotalFrames()).isEqualTo(2);
        assertThat(result.getFrames()).hasSize(2);
        assertThat(result.getFrames().get(0).getShotType()).isEqualTo("CU");
        assertThat(result.getFrames().get(1).getTransition()).isEqualTo("fade");
    }

    @Test
    void generateStoryboard_shouldRetry_whenValidationFails() {
        Script script = Script.builder()
                .id(SCRIPT_ID).projectId(PROJECT_ID)
                .content(Map.of("scenes", List.of()))
                .build();

        String badResponse = "{\"totalFrames\": 0}";
        String goodResponse = """
                {
                    "totalFrames": 1,
                    "frames": [{"frameNumber":1,"sceneNumber":1,"shotType":"CU","bgDescription":"test","characters":[],"dialogueText":"","durationSec":2.0}]
                }
                """;

        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(script));
        when(characterRepository.findByProjectId(PROJECT_ID))
                .thenReturn(List.of(Character.builder().id(UUID.randomUUID()).projectId(PROJECT_ID).name("A").build()));
        when(schemaValidator.validate(anyString(), eq(badResponse)))
                .thenReturn(Set.of(ValidationMessage.builder().message("missing frames").build()));
        when(schemaValidator.validate(anyString(), eq(goodResponse)))
                .thenReturn(Set.of());
        when(llmClient.generate(anyString()))
                .thenReturn(badResponse)
                .thenReturn(goodResponse);
        when(storyboardRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());
        when(storyboardRepository.save(any(Storyboard.class))).thenAnswer(inv -> {
            Storyboard sb = inv.getArgument(0);
            sb.setId(UUID.randomUUID());
            return sb;
        });
        when(sceneRepository.findByScriptIdOrderBySceneNumber(SCRIPT_ID))
                .thenReturn(List.of(Scene.builder().id(UUID.randomUUID()).scriptId(SCRIPT_ID).sceneNumber(1).build()));
        when(frameRepository.save(any())).thenAnswer(inv -> {
            com.example.makemovie.entity.StoryboardFrame f = inv.getArgument(0);
            f.setId(UUID.randomUUID());
            return f;
        });
        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(Project.builder().id(PROJECT_ID).title("Test").mode(ProjectMode.CREATION).build()));

        StoryboardResponse result = storyboardService.generateStoryboard(PROJECT_ID);

        assertThat(result.getTotalFrames()).isEqualTo(1);
        verify(llmClient, times(2)).generate(anyString());
    }
}
