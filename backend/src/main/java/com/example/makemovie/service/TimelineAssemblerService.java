package com.example.makemovie.service;

import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.StoryboardFrame;
import com.example.makemovie.repository.CharacterRepository;
import com.example.makemovie.service.model.CharacterOverlay;
import com.example.makemovie.service.model.TimelineSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Assembles a timeline from storyboard frames, character data, and TTS audio durations.
 *
 * For each storyboard frame:
 * 1. Look up the character expression images from Character entities
 * 2. Combine with TTS audio duration to calculate segment timing
 * 3. Add padding (head/tail silence) for pacing
 * 4. Attach the transition type for the next segment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineAssemblerService {

    private final CharacterRepository characterRepository;

    @Value("${storage.temp-dir:./temp}")
    private String tempDir;

    private static final double PADDING_BEFORE = 0.3;  // seconds
    private static final double PADDING_AFTER = 0.4;   // seconds

    /**
     * Assemble timeline segments from storyboard frames and TTS audio results.
     *
     * @param frames   Storyboard frames in order
     * @param audioMap Frame ID → {audioPath, durationSec} from TtsService
     * @return Ordered list of TimelineSegment ready for FFmpeg
     */
    public List<TimelineSegment> assemble(List<StoryboardFrame> frames,
                                           Map<UUID, TtsService.AudioResult> audioMap) {
        Map<UUID, Character> characterMap = loadCharacters(frames);
        List<TimelineSegment> segments = new ArrayList<>();

        for (int i = 0; i < frames.size(); i++) {
            StoryboardFrame frame = frames.get(i);
            TtsService.AudioResult audio = audioMap.get(frame.getId());

            if (audio == null) {
                audio = new TtsService.AudioResult(null, frame.getDurationSec());
            }

            // Add padding for pacing
            double paddingBefore = i == 0 ? 0.0 : PADDING_BEFORE;
            double paddingAfter = i == frames.size() - 1 ? PADDING_AFTER : 0.0;
            double totalDuration = Math.max(audio.durationSec(), frame.getDurationSec())
                    + paddingBefore + paddingAfter;

            // Map characters from frame JSON to CharacterOverlay records
            List<CharacterOverlay> overlays = buildCharacterOverlays(
                    frame.getCharacters(), characterMap);

            // Determine transition for this segment (applied when moving to next)
            String transition = frame.getTransition();
            if (transition == null || transition.isBlank()) {
                transition = "cut";
            }

            TimelineSegment segment = new TimelineSegment(
                    i,
                    frame.getId(),
                    frame.getSceneId(),
                    frame.getBgImageUrl(),  // will be downloaded later
                    frame.getBgImageUrl(),
                    overlays,
                    audio.audioPath(),
                    audio.durationSec(),
                    paddingBefore,
                    paddingAfter,
                    totalDuration,
                    frame.getSubtitleText(),
                    transition,
                    frame.getShotType() != null ? frame.getShotType() : "MS"
            );

            segments.add(segment);
        }

        log.info("Timeline assembled: {} segments, totalDuration={}s",
                segments.size(),
                String.format("%.1f", segments.stream().mapToDouble(TimelineSegment::totalDurationSec).sum()));
        return segments;
    }

    private Map<UUID, Character> loadCharacters(List<StoryboardFrame> frames) {
        // Collect all unique character IDs from all frames
        Set<UUID> characterIds = new LinkedHashSet<>();
        for (StoryboardFrame frame : frames) {
            for (Map<String, Object> charData : frame.getCharacters()) {
                try {
                    UUID cid = UUID.fromString((String) charData.get("characterId"));
                    characterIds.add(cid);
                } catch (Exception ignored) {
                    // frame might reference character by name instead of ID
                }
            }
        }

        if (characterIds.isEmpty()) return Map.of();

        Map<UUID, Character> result = new LinkedHashMap<>();
        characterRepository.findAllById(characterIds)
                .forEach(c -> result.put(c.getId(), c));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<CharacterOverlay> buildCharacterOverlays(
            List<Map<String, Object>> frameCharacters,
            Map<UUID, Character> characterMap) {

        List<CharacterOverlay> overlays = new ArrayList<>();

        for (Map<String, Object> charData : frameCharacters) {
            String charName = (String) charData.getOrDefault("characterName", "");
            String expression = (String) charData.getOrDefault("expression", "neutral");

            // Try to find the character by name
            Character character = characterMap.values().stream()
                    .filter(c -> c.getName().equals(charName))
                    .findFirst()
                    .orElse(null);

            UUID charId = character != null ? character.getId() : null;

            // Get expression image URL from character entity
            String imageUrl = null;
            if (character != null && character.getExpressions() != null) {
                imageUrl = character.getExpressions().stream()
                        .filter(e -> expression.equals(e.get("type")))
                        .findFirst()
                        .map(e -> (String) e.get("imageUrl"))
                        .orElse(null);
            }

            Map<String, Object> position =
                    (Map<String, Object>) charData.getOrDefault("position", Map.of("x", 0.5, "y", 0.4));

            double x = ((Number) position.getOrDefault("x", 0.5)).doubleValue();
            double y = ((Number) position.getOrDefault("y", 0.4)).doubleValue();
            double scale = ((Number) charData.getOrDefault("scale", 1.0)).doubleValue();
            int layer = ((Number) charData.getOrDefault("layer", 0)).intValue();

            overlays.add(new CharacterOverlay(
                    charId, charName, expression,
                    imageUrl, imageUrl,
                    x, y, scale, layer
            ));
        }

        // Sort by layer
        overlays.sort(Comparator.comparingInt(CharacterOverlay::layer));
        return overlays;
    }
}
