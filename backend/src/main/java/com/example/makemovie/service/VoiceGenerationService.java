package com.example.makemovie.service;

import com.example.makemovie.client.TtsClient;
import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.Project;
import com.example.makemovie.entity.StoryboardFrame;
import com.example.makemovie.enums.StepStatus;
import com.example.makemovie.enums.WorkflowStep;
import com.example.makemovie.event.WorkflowEvent;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.repository.ProjectRepository;
import com.example.makemovie.repository.StoryboardFrameRepository;
import com.example.makemovie.repository.StoryboardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Generates TTS voice audio for each storyboard frame,
 * using per-character voice configuration.
 *
 * After completion, auto-triggers CLIP_GENERATION.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceGenerationService {

    private final TtsClient ttsClient;
    private final StoryboardRepository storyboardRepository;
    private final StoryboardFrameRepository frameRepository;
    private final CharacterRepository characterRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowLogService workflowLogService;
    private final ProgressService progressService;
    private final ImageStorageService imageStorageService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void generateAllVoices(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException("PROJECT_NOT_FOUND", "项目不存在"));

        var storyboard = storyboardRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException("STORYBOARD_NOT_FOUND", "请先生成分镜"));

        List<StoryboardFrame> frames = frameRepository
                .findByStoryboardIdOrderByFrameNumber(storyboard.getId());
        if (frames.isEmpty()) {
            throw new BusinessException("NO_FRAMES", "分镜没有帧");
        }

        List<Character> characters = characterRepository.findByProjectId(projectId);
        Map<String, Character> charMap = new LinkedHashMap<>();
        for (Character c : characters) {
            charMap.put(c.getName(), c);
        }

        // Build voice config for each character
        Map<String, Map<String, Object>> voiceConfigs = new LinkedHashMap<>();
        for (Character c : characters) {
            Map<String, Object> vc = c.getVoiceConfig();
            if (vc == null || vc.isEmpty()) {
                vc = Map.of("voice", "zh-CN-XiaoxiaoNeural", "speed", 1.0, "pitch", 0);
            }
            voiceConfigs.put(c.getName(), vc);
        }

        // Save voice config to project file
        try {
            imageStorageService.storeProjectText(projectId,
                    "04-voice/voice_config.json",
                    objectMapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(voiceConfigs));
        } catch (Exception e) {
            log.warn("Failed to save voice_config.json: {}", e.getMessage());
        }

        int generated = 0;
        for (StoryboardFrame frame : frames) {
            String text = frame.getSubtitleText();
            if (text == null || text.isBlank()) {
                // Silent frame — create a short silence placeholder
                String silentPath = String.format("04-voice/audio/frame_%03d.mp3",
                        frame.getFrameNumber());
                imageStorageService.storeProjectFile(projectId, silentPath,
                        new byte[0], "audio/mpeg");
                frame.setVoiceAudioUrl("silence://" + frame.getFrameNumber());
                frameRepository.save(frame);
                continue;
            }

            // Determine voice for this frame based on the first character
            String voiceName = "zh-CN-XiaoxiaoNeural";
            double speed = 1.0;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> frameChars =
                    (List<Map<String, Object>>) (Object) frame.getCharacters();
            if (frameChars != null && !frameChars.isEmpty()) {
                String charName = (String) frameChars.get(0).get("characterName");
                Map<String, Object> vc = voiceConfigs.get(charName);
                if (vc != null) {
                    voiceName = (String) vc.getOrDefault("voice", voiceName);
                    speed = ((Number) vc.getOrDefault("speed", 1.0)).doubleValue();
                }
            }

            try {
                byte[] audioBytes = ttsClient.synthesizeToBytes(text, voiceName, speed);
                String audioPath = String.format("04-voice/audio/frame_%03d.mp3",
                        frame.getFrameNumber());
                String audioUrl = imageStorageService.storeProjectFile(
                        projectId, audioPath, audioBytes, "audio/mpeg");

                frame.setVoiceAudioUrl(audioUrl);
                frameRepository.save(frame);
                generated++;
            } catch (Exception e) {
                log.warn("TTS failed for frame {}: {}", frame.getFrameNumber(), e.getMessage());
                // Continue with other frames, don't fail the whole batch
            }
        }

        log.info("Voice generation complete: {}/{} frames", generated, frames.size());

        // Update workflow
        eventPublisher.publishEvent(new WorkflowEvent.StepCompletedEvent(
                this, projectId, "VOICE_GENERATION",
                Map.of("totalFrames", frames.size(), "generated", generated),
                0, null));
        progressService.refreshProgress(project);
    }
}
