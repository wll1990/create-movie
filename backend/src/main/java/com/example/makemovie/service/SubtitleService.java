package com.example.makemovie.service;

import com.example.makemovie.service.model.TimelineSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates ASS (Advanced SubStation Alpha) subtitle files
 * for use with FFmpeg's ass filter.
 */
@Slf4j
@Service
public class SubtitleService {

    @Value("${storage.temp-dir:./temp}")
    private String tempDir;

    private static final String ASS_HEADER = """
            [Script Info]
            ScriptType: v4.00+
            PlayResX: 1080
            PlayResY: 1920
            WrapStyle: 2

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
            Style: Default,Microsoft YaHei,48,&H00FFFFFF,&H000088EF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,3,1,2,60,60,80,1

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            """;

    /**
     * Generate an ASS subtitle file from timeline segments.
     * @param segments Timeline segments with absolute start times
     * @param projectId Project UUID for temp file naming
     * @return Path to the generated .ass file
     */
    public String generate(List<TimelineSegment> segments, String projectId) {
        StringBuilder sb = new StringBuilder(ASS_HEADER);

        double currentTime = 0.0;
        for (TimelineSegment seg : segments) {
            String text = seg.subtitleText();
            if (text == null || text.isBlank()) {
                currentTime += seg.totalDurationSec();
                continue;
            }

            double startTime = currentTime;
            double endTime = currentTime + seg.totalDurationSec();

            String startAss = formatAssTime(startTime);
            String endAss = formatAssTime(endTime);

            sb.append(String.format("Dialogue: 0,%s,%s,Default,,0,0,0,,%s%n",
                    startAss, endAss, escapeAssText(text)));

            currentTime = endTime;
        }

        try {
            Path outputPath = Path.of(tempDir, "subtitle", projectId + ".ass");
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, sb.toString());
            log.info("Subtitle file generated: {}, segments={}", outputPath, segments.size());
            return outputPath.toString();
        } catch (Exception e) {
            log.error("Failed to write subtitle file: {}", e.getMessage());
            return null;
        }
    }

    private String formatAssTime(double seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        int s = (int) (seconds % 60);
        int cs = (int) ((seconds - (int) seconds) * 100);
        return String.format("%d:%02d:%02d.%02d", h, m, s, cs);
    }

    private String escapeAssText(String text) {
        return text.replace("\\", "\\\\")
                .replace("\n", "\\N")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }
}
