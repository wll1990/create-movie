package com.example.makemovie.unit;

import com.example.makemovie.service.FfmpegCommandBuilder;
import com.example.makemovie.service.model.CharacterOverlay;
import com.example.makemovie.service.model.TimelineSegment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FfmpegCommandBuilderTest {

    private final FfmpegCommandBuilder builder = new FfmpegCommandBuilder();

    @Test
    void buildCommand_shouldGenerateValidFfmpegCommand() {
        List<TimelineSegment> segments = List.of(
                new TimelineSegment(0, UUID.randomUUID(), UUID.randomUUID(),
                        "/tmp/bg1.png", "http://minio/bg1.png",
                        List.of(new CharacterOverlay(UUID.randomUUID(), "女主",
                                "happy", "/tmp/char1.png", "http://minio/char1.png",
                                0.5, 0.3, 1.0, 0)),
                        "/tmp/audio1.mp3", 3.0, 0.0, 0.4, 3.4,
                        "你好！", "cut", "CU"),
                new TimelineSegment(1, UUID.randomUUID(), UUID.randomUUID(),
                        "/tmp/bg2.png", "http://minio/bg2.png",
                        List.of(),
                        "/tmp/audio2.mp3", 2.5, 0.3, 0.4, 3.2,
                        "再见。", "fade", "MS")
        );

        List<String> command = builder.buildCommand(
                segments, null, null, "/tmp/output.mp4");

        assertThat(command).contains("ffmpeg", "-y");
        assertThat(command).contains("-filter_complex");
        assertThat(command).contains("/tmp/output.mp4");
        assertThat(command).contains("[vout]");
        assertThat(command).contains("[aout]");

        // Find the filter_complex argument
        int fcIdx = command.indexOf("-filter_complex");
        String filter = command.get(fcIdx + 1);
        assertThat(filter).contains("scale=1920:-1");
        assertThat(filter).contains("zoompan=");
        assertThat(filter).contains("concat=n=2:v=1:a=0[vmerged]");
    }

    @Test
    void buildCommand_shouldIncludeAudioPadding() {
        List<TimelineSegment> segments = List.of(
                new TimelineSegment(0, UUID.randomUUID(), UUID.randomUUID(),
                        "/tmp/bg.png", null, List.of(),
                        "/tmp/audio.mp3", 3.0, 0.5, 0.5, 4.0,
                        "字幕", "cut", "CU")
        );

        List<String> command = builder.buildCommand(
                segments, null, null, "/tmp/out.mp4");

        int fcIdx = command.indexOf("-filter_complex");
        String filter = command.get(fcIdx + 1);
        assertThat(filter).contains("adelay=500|500");
    }

    @Test
    void buildCommand_shouldHandleMultipleInputs() {
        List<TimelineSegment> segments = List.of(
                new TimelineSegment(0, UUID.randomUUID(), UUID.randomUUID(),
                        "/tmp/bg.png", null,
                        List.of(
                                new CharacterOverlay(UUID.randomUUID(), "A", "happy",
                                        "/tmp/a.png", null, 0.5, 0.3, 1.0, 0),
                                new CharacterOverlay(UUID.randomUUID(), "B", "sad",
                                        "/tmp/b.png", null, 0.3, 0.5, 0.8, 1)
                        ),
                        "/tmp/audio.mp3", 3.0, 0.0, 0.0, 3.0,
                        "对话", "cut", "FS")
        );

        List<String> command = builder.buildCommand(
                segments, "/tmp/bgm.mp3", "/tmp/sub.ass", "/tmp/out.mp4");

        assertThat(command).contains("-i", "/tmp/bg.png");
        assertThat(command).contains("-i", "/tmp/a.png");
        assertThat(command).contains("-i", "/tmp/b.png");
        assertThat(command).contains("-i", "/tmp/audio.mp3");
        assertThat(command).contains("-i", "/tmp/bgm.mp3");

        int fcIdx = command.indexOf("-filter_complex");
        String filter = command.get(fcIdx + 1);
        // BGM mix should be present
        assertThat(filter).contains("amix=inputs=2:duration=longest");
    }
}
