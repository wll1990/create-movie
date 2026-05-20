package com.example.makemovie.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TTS 客户端
 * Default: edge-tts (free, via subprocess)
 * Fallback: 火山引擎 HTTP API (paid)
 */
@Slf4j
@Component
public class TtsClient {

    private final String provider;
    private final String voice;
    private final String rate;
    private final String pitch;

    public TtsClient(@Value("${tts.provider:edge}") String provider,
                     @Value("${tts.edge-tts.voice:zh-CN-XiaoxiaoNeural}") String voice,
                     @Value("${tts.edge-tts.rate:0}") String rate,
                     @Value("${tts.edge-tts.pitch:0}") String pitch) {
        this.provider = provider;
        this.voice = voice;
        this.rate = rate;
        this.pitch = pitch;
    }

    /**
     * Synthesize text to an MP3 file using edge-tts.
     *
     * @param text       Text to synthesize
     * @param outputPath Path to write the output MP3 file
     * @return Duration in seconds (estimated from file size or returned from tool)
     */
    public double synthesize(String text, Path outputPath) {
        if ("edge".equals(provider)) {
            return synthesizeEdgeTts(text, outputPath);
        }
        log.warn("Unknown TTS provider: {}, falling back to edge-tts", provider);
        return synthesizeEdgeTts(text, outputPath);
    }

    private double synthesizeEdgeTts(String text, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());

            List<String> command = List.of(
                    "edge-tts",
                    "--voice", voice,
                    "--rate=" + rate + "%",
                    "--pitch=" + pitch + "Hz",
                    "--text", text,
                    "--write-media", outputPath.toString()
            );

            log.debug("Running edge-tts: voice={}, textLen={}", voice, text.length());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String line;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                while ((line = reader.readLine()) != null) {
                    log.debug("edge-tts: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("edge-tts failed with exit code " + exitCode);
            }

            File outputFile = outputPath.toFile();
            if (!outputFile.exists() || outputFile.length() == 0) {
                throw new RuntimeException("edge-tts produced empty file: " + outputPath);
            }

            double duration = estimateMp3Duration(outputFile.length());
            log.info("TTS complete: path={}, size={}, duration~={}s",
                    outputPath, outputFile.length(), String.format("%.1f", duration));
            return duration;

        } catch (Exception e) {
            log.error("TTS synthesis failed: {}", e.getMessage());
            throw new RuntimeException("TTS synthesis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Synthesize text to bytes, with configurable voice and speed.
     * Used by VoiceGenerationService for per-character voice generation.
     *
     * @param text  Text to synthesize
     * @param voice Voice name (e.g. zh-CN-XiaoxiaoNeural)
     * @param speed Speed multiplier (1.0 = default)
     * @return MP3 audio bytes
     */
    public byte[] synthesizeToBytes(String text, String voice, double speed) {
        try {
            Path tempFile = Files.createTempFile("tts_", ".mp3");
            try {
                String rateStr = String.format("%+.0f", (speed - 1.0) * 100);
                List<String> command = List.of(
                        "edge-tts",
                        "--voice", voice != null ? voice : this.voice,
                        "--rate=" + rateStr + "%",
                        "--pitch=" + pitch + "Hz",
                        "--text", text,
                        "--write-media", tempFile.toString()
                );

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    while (reader.readLine() != null) { /* consume output */ }
                }

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("edge-tts failed with exit code " + exitCode);
                }

                byte[] bytes = Files.readAllBytes(tempFile);
                if (bytes.length == 0) {
                    // Return minimal valid MP3 silence frame
                    log.warn("TTS produced empty output for text: {}", text.substring(0, Math.min(30, text.length())));
                    return new byte[0];
                }
                log.info("TTS synthesized: voice={}, speed={}, bytes={}", voice, speed, bytes.length);
                return bytes;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            log.error("TTS synthesis failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * List available Chinese (zh-CN) voices from edge-tts.
     * Returns a list of {name, gender, style, description} for the frontend voice picker.
     */
    public List<Map<String, String>> listVoices() {
        List<Map<String, String>> voices = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("edge-tts", "--list-voices")
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();

            for (String line : output.toString().split("\n")) {
                if (!line.startsWith("zh-CN-")) continue;
                String[] parts = line.trim().split("\\s{2,}");
                if (parts.length >= 4) {
                    Map<String, String> voice = new LinkedHashMap<>();
                    voice.put("name", parts[0].trim());
                    voice.put("gender", parts[1].trim());
                    voice.put("style", parts[2].trim());
                    voice.put("description", parts.length >= 4 ? parts[3].trim() : "");
                    voices.add(voice);
                }
            }
            log.info("Found {} zh-CN voices from edge-tts", voices.size());
        } catch (Exception e) {
            log.warn("Failed to list edge-tts voices: {}, returning static list", e.getMessage());
            return getStaticVoiceList();
        }
        if (voices.isEmpty()) {
            return getStaticVoiceList();
        }
        return voices;
    }

    /**
     * Static fallback voice list if edge-tts is not available.
     */
    private List<Map<String, String>> getStaticVoiceList() {
        return List.of(
                Map.of("name", "zh-CN-XiaoxiaoNeural", "gender", "Female", "style", "News, Novel", "description", "Warm"),
                Map.of("name", "zh-CN-XiaoyiNeural", "gender", "Female", "style", "Cartoon, Novel", "description", "Lively"),
                Map.of("name", "zh-CN-YunjianNeural", "gender", "Male", "style", "Sports, Novel", "description", "Passion"),
                Map.of("name", "zh-CN-YunxiNeural", "gender", "Male", "style", "Novel", "description", "Lively, Sunshine"),
                Map.of("name", "zh-CN-YunxiaNeural", "gender", "Male", "style", "Cartoon, Novel", "description", "Cute"),
                Map.of("name", "zh-CN-YunyangNeural", "gender", "Male", "style", "News", "description", "Professional, Reliable"),
                Map.of("name", "zh-CN-liaoning-XiaobeiNeural", "gender", "Female", "style", "Dialect", "description", "Humorous"),
                Map.of("name", "zh-CN-shaanxi-XiaoniNeural", "gender", "Female", "style", "Dialect", "description", "Bright")
        );
    }

    /**
     * Preview a voice — synthesize a short sample (~30 chars) and return audio bytes.
     * Used by the frontend "试听" button before full batch generation.
     */
    public byte[] preview(String text, String voice, double speed) {
        String shortText = text.length() > 40 ? text.substring(0, 40) : text;
        return synthesizeToBytes(shortText, voice, speed);
    }

    /**
     * Rough estimate of MP3 duration from file size.
     * For 128kbps MP3: duration = (fileSizeBytes * 8) / (bitrate)
     */
    private double estimateMp3Duration(long fileSizeBytes) {
        double bitrate = 128_000; // 128 kbps
        return (fileSizeBytes * 8.0) / bitrate;
    }
}
