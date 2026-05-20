package com.example.makemovie.service;

import com.example.makemovie.service.model.CharacterOverlay;
import com.example.makemovie.service.model.TimelineSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Builds FFmpeg filter_complex scripts from a timeline of segments.
 *
 * For each segment:
 * 1. Load background image, apply Ken Burns zoompan for subtle motion
 * 2. Overlay each character image at its position/scale
 * 3. Optionally add subtitles via ASS filter
 * 4. Assign segment label [v{i}]
 *
 * All segments are then concat'd into the final video output [vout].
 * Audio tracks for each segment are similarly concat'd and mixed with BGM.
 *
 * Ken Burns effect: subtle zoom-in at ~3% per second on backgrounds,
 * eliminating the static-slideshow feel. Shot type influences animation:
 * CU/ECU → centered zoom-in, MS/MCU → slight pan, FS/LS → gentle pan.
 */
@Slf4j
@Service
public class FfmpegCommandBuilder {

    private static final int CANVAS_W = 1080;
    private static final int CANVAS_H = 1920;
    private static final int FPS = 30;
    private static final double ZOOM_PER_SECOND = 0.03; // 3% zoom per second

    /**
     * Build a simplified FFmpeg command that concats pre-generated video clips.
     * V2: Video clips are already animated by AI, FFmpeg just joins them.
     *
     * @param clipPaths      Local paths to video clip MP4 files
     * @param audioPaths     Local paths to TTS audio MP3 files
     * @param subtitleTexts  Subtitle text per segment
     * @param subtitlePath   Optional ASS subtitle file
     * @param bgmPath        Optional BGM audio file
     * @param outputPath     Output video file path
     * @return FFmpeg command arguments
     */
    public List<String> buildConcatCommand(List<String> clipPaths,
                                            List<String> audioPaths,
                                            List<String> subtitleTexts,
                                            String subtitlePath,
                                            String bgmPath,
                                            String outputPath) {
        List<String> args = new ArrayList<>();
        args.add("ffmpeg");
        args.add("-y");

        int inputCount = 0;
        // Add video clip inputs
        for (String clipPath : clipPaths) {
            args.add("-i"); args.add(clipPath);
            inputCount++;
        }
        // Add audio inputs
        for (String audioPath : audioPaths) {
            args.add("-i"); args.add(audioPath);
            inputCount++;
        }
        // Add BGM if present
        if (bgmPath != null) {
            args.add("-i"); args.add(bgmPath);
            inputCount++;
        }

        // Build simple concat filter
        StringBuilder fc = new StringBuilder();

        // Video: concat all clips
        List<String> vLabels = new ArrayList<>();
        for (int i = 0; i < clipPaths.size(); i++) {
            String label = String.format("[%d:v]", i);
            vLabels.add(label);
        }
        fc.append(String.join("", vLabels))
                .append(String.format("concat=n=%d:v=1:a=0[vmerged];",
                        clipPaths.size()));

        // Apply subtitles if available
        if (subtitlePath != null && !subtitlePath.isBlank()) {
            String escaped = subtitlePath.replace("\\", "\\\\").replace("'", "\\'");
            fc.append("[vmerged]ass=filename='").append(escaped).append("'[vout];");
        } else {
            fc.append("[vmerged]null[vout];");
        }

        // Audio: mix all clips + BGM
        List<String> aLabels = new ArrayList<>();
        for (int i = 0; i < audioPaths.size(); i++) {
            String label = String.format("[%d:a]", clipPaths.size() + i);
            aLabels.add(label);
        }
        int audioInputCount = aLabels.size();
        String audioMixInputs = String.join("", aLabels);
        if (bgmPath != null) {
            String bgmLabel = String.format("[%d:a]", inputCount - 1);
            fc.append(audioMixInputs).append(bgmLabel)
                    .append(String.format("amix=inputs=%d:duration=longest[aout]",
                            audioInputCount + 1));
        } else {
            fc.append(audioMixInputs)
                    .append(String.format("amix=inputs=%d:duration=longest[aout]",
                            audioInputCount));
        }

        args.add("-filter_complex");
        args.add(fc.toString());
        args.add("-map"); args.add("[vout]");
        args.add("-map"); args.add("[aout]");

        args.add("-c:v"); args.add("libx264");
        args.add("-crf"); args.add("23");
        args.add("-preset"); args.add("fast");
        args.add("-c:a"); args.add("aac");
        args.add("-b:a"); args.add("128k");
        args.add("-pix_fmt"); args.add("yuv420p");
        args.add(outputPath);

        log.info("Concat command: {} clips, {} audio, fc_len={}",
                clipPaths.size(), audioPaths.size(), fc.length());
        return args;
    }

    /**
     * Build a complete FFmpeg command for the video composition (V1 — deprecated).
     * @deprecated Use buildConcatCommand for V2 workflow.
     */
    @Deprecated
    public List<String> buildCommand(List<TimelineSegment> segments,
                                      String bgmPath,
                                      String subtitlePath,
                                      String outputPath) {
        List<String> args = new ArrayList<>();
        args.add("ffmpeg");
        args.add("-y"); // overwrite output

        // Input files: backgrounds (no -t, zoompan controls duration)
        // Character images and audio still use explicit duration
        int totalInputs = 0;
        for (TimelineSegment seg : segments) {
            if (seg.bgImagePath() != null) {
                args.add("-loop"); args.add("1");
                args.add("-i"); args.add(seg.bgImagePath());
                totalInputs++;
            }
            for (CharacterOverlay co : seg.characters()) {
                if (co.imagePath() != null) {
                    args.add("-loop"); args.add("1");
                    args.add("-t"); args.add(String.format("%.1f", seg.totalDurationSec()));
                    args.add("-i"); args.add(co.imagePath());
                    totalInputs++;
                }
            }
            if (seg.audioPath() != null) {
                args.add("-i"); args.add(seg.audioPath());
                totalInputs++;
            }
        }
        if (bgmPath != null) {
            args.add("-i"); args.add(bgmPath);
            totalInputs++;
        }

        // Build filter_complex
        String filterComplex = buildFilterComplex(segments, bgmPath, subtitlePath, totalInputs);
        args.add("-filter_complex");
        args.add(filterComplex);

        // Map output
        args.add("-map");
        args.add("[vout]");
        args.add("-map");
        args.add("[aout]");

        // Encoding settings
        args.add("-c:v");
        args.add("libx264");
        args.add("-crf");
        args.add("23");
        args.add("-preset");
        args.add("fast");
        args.add("-c:a");
        args.add("aac");
        args.add("-b:a");
        args.add("128k");
        args.add("-pix_fmt");
        args.add("yuv420p");

        args.add(outputPath);

        log.info("FFmpeg command: {} inputs, filter_complex length={}",
                totalInputs, filterComplex.length());
        return args;
    }

    private String buildFilterComplex(List<TimelineSegment> segments,
                                       String bgmPath,
                                       String subtitlePath,
                                       int totalInputs) {
        StringBuilder sb = new StringBuilder();
        int inputIdx = 0;

        List<String> videoLabels = new ArrayList<>();
        List<String> audioLabels = new ArrayList<>();

        for (TimelineSegment seg : segments) {
            // Step 1: Background with Ken Burns zoompan
            // Scale up to 1920px wide (1.78x canvas) to provide zoom/pan headroom,
            // then apply a subtle zoom-in effect that elimates the static-slideshow feel.
            String bgLabel = String.format("[%d:v]", inputIdx++);
            String scaledBg = String.format("[bg%d]", seg.index());
            int frameCount = (int) Math.ceil(seg.totalDurationSec() * FPS);
            String zoomExpr = buildZoomExpression(seg.shotType(), frameCount);
            String panXExpr = buildPanXExpression(seg.shotType(), frameCount);
            sb.append(String.format(
                    "%sscale=1920:-1,setsar=1,"
                    + "zoompan=z='%s':d=%d:x='%s':y='ih/2-(ih/zoom/2)':s=%dx%d,fps=%d%s;",
                    bgLabel, zoomExpr, frameCount, panXExpr, CANVAS_W, CANVAS_H, FPS, scaledBg));

            // Step 2: Overlay character images
            String currentVid = scaledBg;
            for (CharacterOverlay overlay : seg.characters()) {
                if (overlay.imagePath() == null) continue;

                String charLabel = String.format("[%d:v]", inputIdx++);
                int charW = (int) (CANVAS_W * overlay.scale() * 0.6);
                String charScaled = String.format("[char%d_%d]", seg.index(),
                        seg.characters().indexOf(overlay));
                sb.append(String.format("%sscale=%d:-1%s;",
                        charLabel, charW, charScaled));

                int x = (int) (overlay.x() * CANVAS_W - charW / 2.0);
                int y = (int) (overlay.y() * CANVAS_H);
                String overlayOut = String.format("[v%d_l%d]",
                        seg.index(), seg.characters().indexOf(overlay));
                sb.append(String.format("%s%soverlay=%d:%d%s;",
                        currentVid, charScaled, x, y, overlayOut));

                currentVid = overlayOut;
            }

            String vidLabel = String.format("[v%d]", seg.index());
            sb.append(String.format("%snull%s;", currentVid, vidLabel));
            videoLabels.add(vidLabel);

            // Step 4: Audio (with silence padding)
            if (seg.audioPath() != null) {
                String audioLabel = String.format("[%d:a]", inputIdx++);
                String paddedAudio = String.format("[a%d]", seg.index());
                sb.append(String.format("%sadelay=%d|%d,apad=whole_dur=%.1f%s;",
                        audioLabel,
                        (int) (seg.paddingBeforeSec() * 1000),
                        (int) (seg.paddingBeforeSec() * 1000),
                        seg.totalDurationSec(),
                        paddedAudio));
                audioLabels.add(paddedAudio);
            } else {
                // Silence for segments without audio
                String silentAudio = String.format("[a%d]", seg.index());
                sb.append(String.format(
                        "aevalsrc=0:duration=%.1f:sample_rate=44100%s;",
                        seg.totalDurationSec(), silentAudio));
                audioLabels.add(silentAudio);
            }
        }

        // Concat all video segments → [vmerged]
        sb.append(String.join("", videoLabels))
                .append(String.format("concat=n=%d:v=1:a=0[vmerged];",
                        videoLabels.size()));

        // Pass through to [vout] — apply subtitles if available
        if (subtitlePath != null && !subtitlePath.isBlank()) {
            // Escape single quotes and backslashes in path for FFmpeg filter
            String escapedPath = subtitlePath
                    .replace("\\", "\\\\")
                    .replace("'", "\\'");
            sb.append("[vmerged]ass=filename='").append(escapedPath).append("'[vout];");
        } else {
            sb.append("[vmerged]null[vout];");
        }

        // Mix all audio segments + BGM
        String audioMixInputs = String.join("", audioLabels);
        if (bgmPath != null) {
            String bgmLabel = String.format("[%d:a]", totalInputs - 1);
            sb.append(audioMixInputs).append(bgmLabel)
                    .append(String.format("amix=inputs=%d:duration=longest[aout]",
                            audioLabels.size() + 1));
        } else {
            sb.append(audioMixInputs)
                    .append(String.format("amix=inputs=%d:duration=longest[aout]",
                            audioLabels.size()));
        }

        return sb.toString();
    }

    /**
     * Build the zoom expression for Ken Burns effect based on shot type.
     * Zoom increases linearly per frame: {@code zoom + speed}.
     *
     * @param shotType   Storyboard shot type (ECU/CU/MCU/MS/FS/LS)
     * @param frameCount Total frames in this segment
     * @return FFmpeg zoompan z-expression string
     */
    private String buildZoomExpression(String shotType, int frameCount) {
        double speed = ZOOM_PER_SECOND / FPS; // default: 0.03 / 30 = 0.001 per frame

        if (shotType == null) {
            return String.format("zoom+%.6f", speed);
        }

        return switch (shotType.toUpperCase()) {
            case "FS", "LS" ->
                // Wide shots: slower zoom, let the pan carry the motion
                String.format("zoom+%.6f", speed * 0.3);
            case "MS" ->
                // Medium shots: moderate zoom
                String.format("zoom+%.6f", speed * 0.6);
            case "ECU" ->
                // Extreme close-up: slightly faster zoom for intensity
                String.format("zoom+%.6f", speed * 1.3);
            default ->
                // CU, MCU, or unknown: standard zoom
                String.format("zoom+%.6f", speed);
        };
    }

    /**
     * Build the horizontal pan expression for Ken Burns effect.
     * Centered for close-ups, gentle pan for wider shots.
     *
     * @param shotType   Storyboard shot type
     * @param frameCount Total frames in this segment
     * @return FFmpeg zoompan x-expression string
     */
    private String buildPanXExpression(String shotType, int frameCount) {
        if (shotType == null) {
            return "iw/2-(iw/zoom/2)";
        }

        return switch (shotType.toUpperCase()) {
            case "FS", "LS" -> {
                // Wide shot: gentle horizontal pan left-to-right
                // Total travel: ~0.6 px/frame at 30fps = 18px/s
                double panSpeed = 0.6;
                double startOffset = -(panSpeed * frameCount / 2.0);
                yield String.format("iw/2-(iw/zoom/2)+%.1f+n*%.1f",
                        startOffset, panSpeed);
            }
            case "MS" -> {
                // Medium shot: very subtle pan
                double panSpeed = 0.4;
                double startOffset = -(panSpeed * frameCount / 2.0);
                yield String.format("iw/2-(iw/zoom/2)+%.1f+n*%.1f",
                        startOffset, panSpeed);
            }
            case "MCU" -> {
                // Medium close-up: barely perceptible float
                double panSpeed = 0.2;
                double startOffset = -(panSpeed * frameCount / 2.0);
                yield String.format("iw/2-(iw/zoom/2)+%.1f+n*%.1f",
                        startOffset, panSpeed);
            }
            default ->
                // CU, ECU, or unknown: stay centered
                "iw/2-(iw/zoom/2)";
        };
    }
}
