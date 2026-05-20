package com.example.makemovie.service;

import com.example.makemovie.client.TtsClient;
import com.example.makemovie.entity.StoryboardFrame;
import com.example.makemovie.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * TTS Service — batch generates audio files for all dialogues in storyboard frames.
 * Returns a map of frameId → {audioPath, durationSec}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final TtsClient ttsClient;

    @Value("${storage.temp-dir:./temp}")
    private String tempDir;

    /**
     * Generate TTS audio for all frames that have subtitle text.
     * Skips frames with empty subtitleText.
     */
    public Map<UUID, AudioResult> generateAll(List<StoryboardFrame> frames) {
        Map<UUID, AudioResult> results = new LinkedHashMap<>();

        for (StoryboardFrame frame : frames) {
            String text = frame.getSubtitleText();
            if (text == null || text.isBlank()) {
                // Silent segment, use min duration
                results.put(frame.getId(), new AudioResult(null, 0.5));
                continue;
            }

            Path outputPath = Path.of(tempDir, "tts",
                    frame.getId().toString() + ".mp3");

            try {
                double duration = ttsClient.synthesize(text, outputPath);
                results.put(frame.getId(),
                        new AudioResult(outputPath.toString(), duration));
            } catch (Exception e) {
                log.error("TTS failed for frame {}: {}", frame.getId(), e.getMessage());
                // Fallback: estimate duration from text length
                double estimatedDur = text.length() * 0.25; // ~4 chars/sec
                results.put(frame.getId(),
                        new AudioResult(null, Math.max(estimatedDur, 1.0)));
            }
        }

        return results;
    }

    public record AudioResult(String audioPath, double durationSec) {}
}
