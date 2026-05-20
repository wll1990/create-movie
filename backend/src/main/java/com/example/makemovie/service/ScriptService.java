package com.example.makemovie.service;

import com.example.makemovie.client.LlmClient;
import com.example.makemovie.dto.response.ScriptResponse;
import com.example.makemovie.entity.Episode;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.Script;
import com.example.makemovie.event.WorkflowEvent.*;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.CompositionRepository;
import com.example.makemovie.repository.CompositionTaskRepository;
import com.example.makemovie.repository.ClipTaskRepository;
import com.example.makemovie.repository.EpisodeRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.ScriptRepository;
import com.example.makemovie.repository.StoryboardFrameRepository;
import com.example.makemovie.repository.StoryboardRepository;
import com.example.makemovie.validation.JsonSchemaValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final LlmClient llmClient;
    private final ScriptRepository scriptRepository;
    private final EpisodeRepository episodeRepository;
    private final ProjectRepository projectRepository;
    private final CharacterRepository characterRepository;
    private final StoryboardRepository storyboardRepository;
    private final StoryboardFrameRepository storyboardFrameRepository;
    private final CompositionRepository compositionRepository;
    private final CompositionTaskRepository compositionTaskRepository;
    private final ClipTaskRepository clipTaskRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final WorkflowEngine workflowEngine;
    private final PromptLoader promptLoader;
    private final JsonSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    private static final String SCHEMA_PATH = "schemas/script-output-schema.json";
    private static final int MAX_RETRY = 3;

    @Transactional
    public ScriptResponse generateScript(UUID projectId,
                                          String track, String theme,
                                          Integer duration,
                                          List<Map<String, Object>> characters) {
        return generateScript(projectId, track, theme, duration, characters, null);
    }

    @Transactional
    public ScriptResponse generateScript(UUID projectId,
                                          String track, String theme,
                                          Integer duration,
                                          List<Map<String, Object>> characters,
                                          Integer episodeNumber) {
        // Build previous context for episodes > 1
        String previousContext = buildPreviousContext(projectId, episodeNumber);

        String prompt = buildPrompt(track, theme, duration, characters, previousContext,
                episodeNumber != null ? episodeNumber : 1);

        // Engine: mark step started + reset downstream
        Map<String, Object> inputData = Map.of("track", track, "theme", theme,
                "episodeNumber", episodeNumber != null ? episodeNumber : 1);
        eventPublisher.publishEvent(new StepStartedEvent(
                this, projectId, "SCRIPT_CREATION", prompt, inputData));
        resetDownstreamIfExists(projectId);

        for (int attempt = 0; attempt < MAX_RETRY; attempt++) {
            try {
                long startTime = System.currentTimeMillis();
                String response = llmClient.generate(prompt);
                long elapsed = System.currentTimeMillis() - startTime;

                Set<ValidationMessage> errors = validateResponse(response);
                if (!errors.isEmpty()) {
                    log.warn("Script validation failed (attempt {}): {}", attempt + 1, errors);
                    if (attempt < MAX_RETRY - 1) {
                        prompt = buildRetryPrompt(prompt, errors);
                        continue;
                    }
                    eventPublisher.publishEvent(new StepFailedEvent(
                            this, projectId, "SCRIPT_CREATION",
                            "格式校验失败: " + errors));
                    throw new BusinessException("SCRIPT_VALIDATION_FAILED",
                            "剧本格式校验失败，已重试" + MAX_RETRY + "次");
                }

                Map<String, Object> content = objectMapper.readValue(response,
                        new TypeReference<>() {});

                // Delete previous script if exists
                scriptRepository.findByProjectId(projectId)
                        .ifPresent(scriptRepository::delete);

                Script script = Script.builder()
                        .projectId(projectId)
                        .title((String) content.get("title"))
                        .track(track)
                        .duration((Integer) content.getOrDefault("duration", duration))
                        .content(content)
                        .status("COMPLETED")
                        .build();
                script = scriptRepository.save(script);

                eventPublisher.publishEvent(new StepCompletedEvent(
                        this, projectId, "SCRIPT_CREATION", content, elapsed, prompt));

                // Save episode summary for cross-episode continuity
                saveEpisodeSummary(projectId, episodeNumber, response);

                log.info("Script generated: projectId={}, title={}, episode={}",
                        projectId, script.getTitle(), episodeNumber);
                return ScriptResponse.fromEntity(script);

            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Script generation error (attempt {})", attempt + 1, e);
                if (attempt == MAX_RETRY - 1) {
                    eventPublisher.publishEvent(new StepFailedEvent(
                            this, projectId, "SCRIPT_CREATION", e.getMessage()));
                    throw new BusinessException("SCRIPT_GENERATION_FAILED",
                            "剧本生成失败: " + e.getMessage());
                }
            }
        }
        throw new BusinessException("SCRIPT_GENERATION_FAILED", "剧本生成失败");
    }

    public ScriptResponse getScript(UUID projectId) {
        Script script = scriptRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("SCRIPT_NOT_FOUND",
                        "该项目尚未生成剧本: " + projectId));
        return ScriptResponse.fromEntity(script);
    }

    @Transactional
    public ScriptResponse updateScript(UUID projectId, String title, Map<String, Object> content) {
        Script script = scriptRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("SCRIPT_NOT_FOUND",
                        "该项目尚未生成剧本: " + projectId));

        script.setTitle(title);
        script.setContent(content);
        script.setVersion(script.getVersion() + 1);
        script = scriptRepository.save(script);

        log.info("Script updated: projectId={}, version={}", projectId, script.getVersion());
        return ScriptResponse.fromEntity(script);
    }

    private void resetDownstreamIfExists(UUID projectId) {
        // Delete existing storyboards, compositions (but NOT characters in multi-episode projects)
        long episodeCount = episodeRepository.countByProjectId(projectId);
        if (episodeCount <= 1) {
            // Single episode: safe to delete characters
            if (!characterRepository.findByProjectId(projectId).isEmpty()) {
                characterRepository.deleteAll(characterRepository.findByProjectId(projectId));
                log.info("ScriptService: deleted existing characters for project={} (single episode)", projectId);
            }
        } else {
            log.info("ScriptService: keeping existing characters ({} episodes use them)", episodeCount);
        }
        storyboardRepository.findByProjectId(projectId).ifPresent(sb -> {
            storyboardFrameRepository.deleteAll(
                    storyboardFrameRepository.findByStoryboardIdOrderByFrameNumber(sb.getId()));
            storyboardRepository.delete(sb);
            log.info("ScriptService: deleted existing storyboard for project={}", projectId);
        });
        if (!compositionRepository.findByProjectId(projectId).isEmpty()) {
            compositionRepository.findByProjectId(projectId).forEach(comp -> {
                compositionTaskRepository.deleteAll(compositionTaskRepository.findByCompositionId(comp.getId()));
            });
            compositionRepository.deleteAll(compositionRepository.findByProjectId(projectId));
            log.info("ScriptService: deleted existing compositions for project={}", projectId);
        }
        clipTaskRepository.findByProjectIdOrderByFrameNumber(projectId).forEach(ct -> {
            clipTaskRepository.delete(ct);
        });

        // Reset downstream workflow steps to PENDING
        workflowEngine.resetDownstream(projectId, "SCRIPT_CREATION");
    }

    private String buildPrompt(String track, String theme,
                                Integer duration,
                                List<Map<String, Object>> characters,
                                String previousContext,
                                int episodeNumber) {
        String schemaStr = loadSchemaString();
        Map<String, String> vars = new java.util.LinkedHashMap<>();
        vars.put("track", track);
        vars.put("theme", theme);
        vars.put("duration", String.valueOf(duration));
        vars.put("characters", characters != null && !characters.isEmpty()
                ? characters.toString() : "请根据赛道和主题自动设计角色");
        vars.put("episodeNumber", String.valueOf(episodeNumber));
        vars.put("previousContext", previousContext != null && !previousContext.isEmpty()
                ? "【前情提要】\n" + previousContext + "\n---\n" : "");
        String prompt = promptLoader.load("script-system", vars);
        return prompt + "\n\n请严格按照以下JSON Schema输出：\n" + schemaStr;
    }

    private String buildPreviousContext(UUID projectId, Integer episodeNumber) {
        if (episodeNumber == null || episodeNumber <= 1) return "";
        List<Episode> episodes = episodeRepository.findByProjectIdOrderByEpisodeNumber(projectId);
        StringBuilder sb = new StringBuilder();
        for (Episode ep : episodes) {
            if (ep.getEpisodeNumber() < episodeNumber && ep.getSummary() != null) {
                sb.append("第").append(ep.getEpisodeNumber()).append("集: ")
                        .append(ep.getSummary()).append("\n");
            }
        }
        return sb.toString();
    }

    private void saveEpisodeSummary(UUID projectId, Integer episodeNumber, String scriptContent) {
        if (episodeNumber == null) return;
        try {
            // Use LLM to generate a concise summary
            String summaryPrompt = "请用一句话（50字以内）总结以下剧本的核心剧情：\n" +
                    (scriptContent.length() > 500 ? scriptContent.substring(0, 500) : scriptContent) +
                    "\n只输出摘要，不要其他内容。";
            String summary = llmClient.generate(summaryPrompt);
            episodeRepository.findByProjectIdAndEpisodeNumber(projectId, episodeNumber)
                    .ifPresent(ep -> {
                        ep.setSummary(summary != null ? summary.trim() : "");
                        episodeRepository.save(ep);
                        log.info("Episode {} summary saved", episodeNumber);
                    });
        } catch (Exception e) {
            log.warn("Failed to save episode summary: {}", e.getMessage());
        }
    }

    private String buildRetryPrompt(String originalPrompt, Set<ValidationMessage> errors) {
        return originalPrompt + "\n\n上次输出的JSON格式有误，请修正以下问题:\n" + errors;
    }

    private Set<ValidationMessage> validateResponse(String response) {
        String schema = loadSchemaString();
        return schemaValidator.validate(schema, response);
    }

    private String loadSchemaString() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream(SCHEMA_PATH)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("SCHEMA_LOAD_FAILED", "无法加载JSON Schema");
        }
    }
}
