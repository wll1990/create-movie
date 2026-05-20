package com.example.makemovie.unit;

import com.example.makemovie.service.SubtitleService;
import com.example.makemovie.service.model.CharacterOverlay;
import com.example.makemovie.service.model.TimelineSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubtitleServiceTest {

    @Test
    void generate_shouldCreateAssFile(@TempDir Path tempDir) throws IOException {
        SubtitleService service = new SubtitleService();
        ReflectionTestUtils.setField(service, "tempDir", tempDir.toString());

        List<TimelineSegment> segments = List.of(
                new TimelineSegment(0, UUID.randomUUID(), UUID.randomUUID(),
                        null, null, List.of(), null, 3.0, 0.0, 0.0, 3.0,
                        "你好世界！", "cut", "MS"),
                new TimelineSegment(1, UUID.randomUUID(), UUID.randomUUID(),
                        null, null, List.of(), null, 2.5, 0.0, 0.0, 2.5,
                        "再见。", "fade", "MS")
        );

        String assPath = service.generate(segments, "test-project");
        assertThat(assPath).isNotNull();

        // Verify the file exists and contains expected content
        Path file = Path.of(assPath);
        assertThat(Files.exists(file)).isTrue();

        String content = Files.readString(file);
        assertThat(content).contains("PlayResX: 1080");
        assertThat(content).contains("PlayResY: 1920");
        assertThat(content).contains("你好世界！");
        assertThat(content).contains("再见。");
        assertThat(content).contains("Default,Microsoft YaHei,48");
    }

    @Test
    void generate_shouldSkipEmptySubtitles(@TempDir Path tempDir) throws IOException {
        SubtitleService service = new SubtitleService();
        ReflectionTestUtils.setField(service, "tempDir", tempDir.toString());

        List<TimelineSegment> segments = List.of(
                new TimelineSegment(0, UUID.randomUUID(), UUID.randomUUID(),
                        null, null, List.of(), null, 3.0, 0.0, 0.0, 3.0,
                        null, "cut", "MS"), // No subtitle
                new TimelineSegment(1, UUID.randomUUID(), UUID.randomUUID(),
                        null, null, List.of(), null, 2.0, 0.0, 0.0, 2.0,
                        "台词", "cut", "MS")
        );

        String assPath = service.generate(segments, "test-skip");
        String content = Files.readString(Path.of(assPath));

        assertThat(content).contains("台词");
        // Should only have the default style line + 1 dialogue
        long dialogueCount = content.lines()
                .filter(l -> l.startsWith("Dialogue:"))
                .count();
        assertThat(dialogueCount).isEqualTo(1);
    }
}
