package com.example.makemovie.service;

import com.example.makemovie.client.LlmClient;
import com.example.makemovie.dto.response.CharacterResponse;
import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Script;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.event.WorkflowEvent;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.ScriptRepository;
import com.example.makemovie.validation.JsonSchemaValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final LlmClient llmClient;
    private final CharacterImageService characterImageService;
    private final CharacterRepository characterRepository;
    private final ScriptRepository scriptRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowLogService workflowLogService;
    private final ProgressService progressService;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private static final int MAX_RETRY = 3;

    @Transactional
    public List<CharacterResponse> generateCharacters(UUID projectId) {
        Script script = scriptRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("SCRIPT_NOT_FOUND",
                        "请先生成剧本"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes =
                (List<Map<String, Object>>) script.getContent().get("scenes");
        Set<String> characterNames = extractCharacterNames(scenes);

        if (characterNames.isEmpty()) {
            throw new BusinessException("NO_CHARACTERS", "剧本中未找到角色");
        }

        // Guard: reuse existing characters for episodes > 1
        List<Character> existingChars = characterRepository.findByProjectId(projectId);
        if (!existingChars.isEmpty()) {
            log.info("项目 {} 已有 {} 个角色，跳过生成直接复用",
                    projectId, existingChars.size());
            var existingResponses = existingChars.stream()
                    .map(CharacterResponse::fromEntity).toList();
            // Publish completed event for reused characters
            eventPublisher.publishEvent(new WorkflowEvent.StepCompletedEvent(
                    this, projectId, "CHARACTER_DESIGN",
                    Map.of("reused", true, "characters", existingChars.stream()
                            .map(c -> Map.of("id", c.getId().toString(), "name", c.getName()))
                            .toList()),
                    0, null));
            // Auto-trigger next step
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "项目不存在"));
            workflowLogService.updateStatus(projectId, WorkflowStep.STORYBOARD_DESIGN,
                    StepStatus.RUNNING, null, null, 0);
            progressService.refreshProgress(project);
            return existingResponses;
        }

        log.info("Generating character designs for {} characters: {}", characterNames.size(), characterNames);

        // Publish step started event (replaces direct WorkflowLogService call)
        eventPublisher.publishEvent(new WorkflowEvent.StepStartedEvent(
                this, projectId, "CHARACTER_DESIGN",
                null, Map.of("characterNames", characterNames)));

        List<Character> characters = new ArrayList<>();
        List<Map<String, Object>> charPromptEntries = new ArrayList<>();
        List<Map<String, Object>> progressDetails = new ArrayList<>();

        int total = characterNames.size();
        int idx = 0;
        for (String name : characterNames) {
            idx++;
            // Save progress: starting this character
            String charPrompt = buildCharacterPromptForProgress(name, scenes);
            Map<String, Object> progress = new LinkedHashMap<>();
            progress.put("currentIndex", idx);
            progress.put("totalCharacters", total);
            progress.put("currentCharacter", name);
            progress.put("currentPrompt", charPrompt);
            progress.put("completedDetails", progressDetails);

            workflowLogService.updateStatus(projectId, WorkflowStep.CHARACTER_DESIGN,
                    StepStatus.RUNNING,
                    Map.of("characterNames", characterNames),
                    progress, 0, charPrompt);

            long charStart = System.currentTimeMillis();
            Character character = generateSingleCharacter(projectId, name, scenes, charPromptEntries);
            long elapsed = System.currentTimeMillis() - charStart;

            characters.add(character);
            progressDetails.add(Map.of(
                    "name", name,
                    "elapsedMs", elapsed,
                    "status", "COMPLETED"
            ));
        }

        // Store prompt for later use (defer completion until all images are done)
        Map<String, Object> promptStruct = Map.of("characters", charPromptEntries);
        String promptJson;
        try {
            promptJson = objectMapper.writeValueAsString(promptStruct);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            promptJson = "{\"characters\":" + charPromptEntries + "}";
        }

        // Keep CHARACTER_DESIGN as RUNNING — waiting for image generation to complete
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("phase", "IMAGES_PENDING");
        progress.put("totalCharacters", characters.size());
        progress.put("characters", characters.stream()
                .map(c -> Map.of("id", c.getId().toString(), "name", c.getName(),
                        "status", c.getImageGenerationStatus()))
                .toList());
        workflowLogService.updateStatus(projectId, WorkflowStep.CHARACTER_DESIGN,
                StepStatus.RUNNING,
                progress,
                Map.of("charactersCreated", characters.size()),
                0, promptJson);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "项目不存在"));
        progressService.refreshProgress(project);

        // DO NOT auto-trigger STORYBOARD_DESIGN — wait until all character images are complete
        // CharacterImageService will publish StepCompletedEvent when all characters are done

        return characters.stream().map(CharacterResponse::fromEntity).toList();
    }

    @Transactional
    public CharacterResponse regenerateCharacter(UUID projectId, UUID charId, String customPrompt) {
        com.example.makemovie.entity.Character existing = characterRepository.findById(charId)
                .orElseThrow(() -> new BusinessException("CHARACTER_NOT_FOUND", "角色不存在: " + charId));

        Script script = scriptRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("SCRIPT_NOT_FOUND", "请先生成剧本"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes =
                (List<Map<String, Object>>) script.getContent().get("scenes");

        String name = existing.getName();

        // If custom prompt provided, use it directly; otherwise build default prompt
        if (customPrompt != null && !customPrompt.isBlank()) {
            // Delete and regenerate with custom prompt
            characterRepository.delete(existing);
            com.example.makemovie.entity.Character character = generateCharacterWithPrompt(
                    projectId, name, scenes, customPrompt);
            return CharacterResponse.fromEntity(character);
        }

        // Default: re-generate with standard scene-filtered prompt
        characterRepository.delete(existing);
        List<Map<String, Object>> dummyList = new ArrayList<>();
        com.example.makemovie.entity.Character character = generateSingleCharacter(
                projectId, name, scenes, dummyList);
        return CharacterResponse.fromEntity(character);
    }

    public List<CharacterResponse> getCharacters(UUID projectId) {
        return characterRepository.findByProjectId(projectId)
                .stream()
                .map(CharacterResponse::fromEntity)
                .toList();
    }

    private Character generateSingleCharacter(UUID projectId, String name,
                                               List<Map<String, Object>> scenes,
                                               List<Map<String, Object>> charPromptEntries) {
        // Delete existing character with same name
        characterRepository.findByProjectId(projectId).stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .ifPresent(c -> characterRepository.delete(c));

        // Build character-specific scene context
        String sceneContext = buildCharacterSceneContext(name, scenes);

        String prompt = promptLoader.load("character-design", Map.of(
                "name", name,
                "scenes", sceneContext
        ));
        charPromptEntries.add(Map.of("name", name, "prompt", prompt));

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                String response = llmClient.generate(prompt);
                Map<String, Object> data = objectMapper.readValue(response,
                        new TypeReference<>() {});

                Character character = Character.builder()
                        .projectId(projectId)
                        .name(name)
                        .role(toString(data.get("role")))
                        .gender(toString(data.get("gender")))
                        .ageRange(toString(data.get("ageRange")))
                        .personality(toString(data.get("personality")))
                        .appearance(getMap(data, "appearance"))
                        .voiceConfig(Map.of(
                                "voice", "zh-CN-XiaoxiaoNeural",
                                "speed", 1.0,
                                "pitch", 0
                        ))
                        .build();

                Character saved = characterRepository.save(character);
                log.info("Character saved: name={}, id={}", name, saved.getId());

                // Fire async image generation (gacha-style: 4 candidates)
                String appearanceDesc = buildAppearanceDescription(data);
                characterImageService.generateCandidates(projectId, saved.getId(), name, appearanceDesc);

                return saved;

            } catch (Exception e) {
                log.error("Character generation error (attempt {}): name={}", attempt + 1, name, e);
                if (attempt == MAX_RETRY - 1) {
                    throw new BusinessException("CHARACTER_GEN_FAILED",
                            "角色生成失败: " + name + " - " + e.getMessage());
                }
            }
        }
        throw new BusinessException("CHARACTER_GEN_FAILED", "角色生成失败: " + name);
    }

    private Character generateCharacterWithPrompt(UUID projectId, String name,
                                                    List<Map<String, Object>> scenes,
                                                    String customPrompt) {
        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                String response = llmClient.generate(customPrompt);
                Map<String, Object> data = objectMapper.readValue(response,
                        new TypeReference<>() {});
                Character character = Character.builder()
                        .projectId(projectId)
                        .name(name)
                        .role(toString(data.get("role")))
                        .gender(toString(data.get("gender")))
                        .ageRange(toString(data.get("ageRange")))
                        .personality(toString(data.get("personality")))
                        .appearance(getMap(data, "appearance"))
                        .voiceConfig(Map.of("voice", "zh-CN-XiaoxiaoNeural",
                                "speed", 1.0, "pitch", 0))
                        .build();
                Character saved = characterRepository.save(character);
                // Fire async image generation
                String appearanceDesc = buildAppearanceDescription(data);
                characterImageService.generateCandidates(projectId, saved.getId(), name, appearanceDesc);
                return saved;
            } catch (Exception e) {
                log.error("Character regeneration error (attempt {}): name={}", attempt + 1, name, e);
                if (attempt == MAX_RETRY - 1) {
                    throw new BusinessException("CHARACTER_REGENERATE_FAILED",
                            "角色重新生成失败: " + name + " - " + e.getMessage());
                }
            }
        }
        throw new BusinessException("CHARACTER_REGENERATE_FAILED", "角色重新生成失败: " + name);
    }

    private String buildCharacterPromptForProgress(String charName, List<Map<String, Object>> scenes) {
        String sceneContext = buildCharacterSceneContext(charName, scenes);
        return promptLoader.load("character-design", Map.of(
                "name", charName,
                "scenes", sceneContext
        ));
    }

    private String buildCharacterSceneContext(String charName, List<Map<String, Object>> scenes) {
        StringBuilder ctx = new StringBuilder();
        int sceneCount = 0;

        for (Map<String, Object> scene : scenes) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dialogues =
                    (List<Map<String, Object>>) scene.get("dialogues");
            if (dialogues == null) continue;

            // Check if this character appears in this scene
            boolean appears = dialogues.stream()
                    .anyMatch(d -> charName.equals(d.get("characterName")));
            if (!appears) continue;

            sceneCount++;
            if (sceneCount > 5) break;

            String location = scene.get("location") != null
                    ? scene.get("location").toString() : "未知地点";
            String timeOfDay = scene.get("timeOfDay") != null
                    ? scene.get("timeOfDay").toString() : "";
            String summary = scene.get("summary") != null
                    ? scene.get("summary").toString() : "";

            ctx.append(String.format("\n【场景%d】%s %s\n", sceneCount, location, timeOfDay));
            ctx.append("场景概要: ").append(summary).append("\n");

            // Collect this character's lines and emotions
            ctx.append("该角色台词与情绪:\n");
            for (Map<String, Object> d : dialogues) {
                if (charName.equals(d.get("characterName"))) {
                    String text = d.get("text") != null ? d.get("text").toString() : "";
                    String emotion = d.get("emotion") != null ? d.get("emotion").toString() : "neutral";
                    ctx.append(String.format("  - [%s] \"%s\"\n", emotion, text));
                }
            }

            // Other characters in this scene
            List<String> others = new ArrayList<>();
            for (Map<String, Object> d : dialogues) {
                String otherName = d.get("characterName") != null
                        ? d.get("characterName").toString() : "";
                if (!otherName.equals(charName) && !others.contains(otherName)) {
                    others.add(otherName);
                }
            }
            if (!others.isEmpty()) {
                ctx.append("同场景角色: ").append(String.join("、", others)).append("\n");
            }
        }

        if (sceneCount == 0) {
            // Fallback: character has no dialogue in any scene, use generic context
            ctx.append("该角色在本集剧本中出现，请根据赛道和剧情风格为其设计形象。\n");
        }

        return ctx.toString();
    }

    private Set<String> extractCharacterNames(List<Map<String, Object>> scenes) {
        Set<String> names = new LinkedHashSet<>();
        if (scenes == null) return names;
        for (Map<String, Object> scene : scenes) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dialogues =
                    (List<Map<String, Object>>) scene.get("dialogues");
            if (dialogues != null) {
                for (Map<String, Object> d : dialogues) {
                    String name = (String) d.get("characterName");
                    if (name == null || name.isBlank()) continue;
                    // Normalize: strip parenthetical qualifiers like "顾客（陆沉）" → "陆沉"
                    // Also handle half-width parens: "顾客(陆沉)"
                    String normalized = normalizeCharacterName(name);
                    // If the normalized name already exists, skip this qualifier variant
                    if (names.contains(normalized)) continue;
                    // If a qualifier variant of this name already exists, replace it
                    names.removeIf(existing -> isQualifierVariant(existing, normalized));
                    names.add(normalized);
                }
            }
        }
        return names;
    }

    /** Strip parenthetical qualifiers: "顾客（陆沉）" → "陆沉", "顾客(陆沉)" → "陆沉" */
    private String normalizeCharacterName(String name) {
        // Strip Chinese full-width parens: "XXX（YYY）" → "YYY"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[（(]([^）)]+)[）)]").matcher(name);
        if (m.find()) {
            String inner = m.group(1).trim();
            if (!inner.isBlank()) return inner;
        }
        return name.trim();
    }

    /** Check if existing is a qualifier variant of normalized, e.g. "陆沉（总裁）" vs "陆沉" */
    private boolean isQualifierVariant(String existing, String normalized) {
        return existing.contains(normalized) && (existing.contains("（") || existing.contains("("));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> data, String key) {
        Object val = data.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return Map.of();
    }

    private String toString(Object val) {
        return val != null ? val.toString() : "";
    }

    private String buildAppearanceDescription(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append(toString(data.get("gender"))).append("，");
        sb.append(toString(data.get("ageRange"))).append("，");
        @SuppressWarnings("unchecked")
        Map<String, Object> appearance = (Map<String, Object>) data.get("appearance");
        if (appearance != null) {
            if (appearance.containsKey("hairStyle"))
                sb.append(appearance.get("hairStyle")).append("发型，");
            if (appearance.containsKey("hairColor"))
                sb.append(appearance.get("hairColor")).append("发色，");
            if (appearance.containsKey("clothing"))
                sb.append("穿着").append(appearance.get("clothing")).append("，");
            if (appearance.containsKey("features"))
                sb.append(appearance.get("features"));
        }
        return sb.toString();
    }
}
