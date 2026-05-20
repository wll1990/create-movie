package com.example.makemovie.unit;

import com.example.makemovie.client.LlmClient;
import com.example.makemovie.dto.response.CharacterResponse;
import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Script;
import com.example.makemovie.enums.ProjectMode;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.ScriptRepository;
import com.example.makemovie.service.CharacterImageService;
import com.example.makemovie.service.CharacterService;
import com.example.makemovie.service.ProgressService;
import com.example.makemovie.service.WorkflowLogService;
import com.example.makemovie.validation.JsonSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CharacterServiceTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private CharacterImageService characterImageService;
    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private ScriptRepository scriptRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkflowLogService workflowLogService;
    @Mock
    private ProgressService progressService;
    @Mock
    private com.example.makemovie.service.PromptLoader promptLoader;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private CharacterService characterService;
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(promptLoader.load(anyString(), anyMap())).thenReturn("character design prompt");
        characterService = new CharacterService(
                llmClient, characterImageService,
                characterRepository, scriptRepository, projectRepository,
                workflowLogService, progressService, promptLoader, new ObjectMapper(),
                eventPublisher);
    }

    @Test
    void generateCharacters_shouldReturnCharacters_whenScriptHasRoles() {
        // Given
        Script script = Script.builder()
                .projectId(PROJECT_ID)
                .title("Test")
                .content(Map.of("scenes", List.of(
                        Map.of("dialogues", List.of(
                                Map.of("characterName", "女主", "text", "你好"),
                                Map.of("characterName", "男主", "text", "你好")
                        ))
                )))
                .build();

        String llmResponse = """
                {
                    "role": "PROTAGONIST",
                    "gender": "女",
                    "ageRange": "青年",
                    "personality": "温柔善良",
                    "appearance": {"hairStyle": "长发", "eyeColor": "棕色"}
                }
                """;

        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.of(script));
        when(llmClient.generate(anyString())).thenReturn(llmResponse);
        when(characterRepository.findByProjectId(PROJECT_ID))
                .thenReturn(List.of());
        when(characterRepository.save(any(Character.class))).thenAnswer(inv -> {
            Character c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(projectRepository.findById(PROJECT_ID))
                .thenReturn(Optional.of(Project.builder().id(PROJECT_ID).title("Test").mode(ProjectMode.CREATION).build()));

        // When
        List<CharacterResponse> result = characterService.generateCharacters(PROJECT_ID);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("女主");
        assertThat(result.get(1).getName()).isEqualTo("男主");
        verify(llmClient, times(2)).generate(anyString());
    }

    @Test
    void generateCharacters_shouldThrow_whenNoScript() {
        when(scriptRepository.findByProjectId(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterService.generateCharacters(PROJECT_ID))
                .isInstanceOf(com.example.makemovie.exception.BusinessException.class)
                .hasMessageContaining("请先生成剧本");
    }

    @Test
    void getCharacters_shouldReturnList() {
        when(characterRepository.findByProjectId(PROJECT_ID)).thenReturn(List.of(
                Character.builder()
                        .id(UUID.randomUUID())
                        .projectId(PROJECT_ID)
                        .name("女主")
                        .role("PROTAGONIST")
                        .build()
        ));

        List<CharacterResponse> result = characterService.getCharacters(PROJECT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("女主");
    }
}
