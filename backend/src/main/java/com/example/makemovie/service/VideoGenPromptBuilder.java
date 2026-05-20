package com.example.makemovie.service;

import com.example.makemovie.entity.Character;
import com.example.makemovie.entity.StoryboardFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds specialized prompts for AI video generation (可灵/即梦 etc.)
 * from storyboard frame context, character data, and scene information.
 *
 * Each prompt includes: character appearance reference, expression,
 * background, shot type, camera movement, dialogue sync, and style.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoGenPromptBuilder {

    private final PromptLoader promptLoader;

    /**
     * Build a video generation prompt for a single storyboard frame.
     *
     * @param frame        Storyboard frame with shot/composition data
     * @param characters   Character entities for this project
     * @param projectTrack Project track (都市甜宠/悬疑 etc.) for style
     * @return Complete prompt string for AI video generation
     */
    public String buildPrompt(StoryboardFrame frame,
                              List<Character> characters,
                              String projectTrack) {
        StringBuilder sb = new StringBuilder();

        // 1. Character descriptions
        List<Map<String, Object>> frameChars = frame.getCharacters();
        if (frameChars != null && !frameChars.isEmpty()) {
            sb.append("【角色描述】\n");
            for (Map<String, Object> charData : frameChars) {
                String charName = (String) charData.getOrDefault("characterName", "");
                String expression = (String) charData.getOrDefault("expression", "neutral");

                // Look up character details
                Character character = findCharacter(characters, charName);
                if (character != null) {
                    sb.append(String.format("【%s】，%s，%s，%s，",
                            charName,
                            character.getGender() != null ? character.getGender() : "",
                            character.getAgeRange() != null ? character.getAgeRange() : "",
                            character.getPersonality() != null ? character.getPersonality() : ""));
                    // Add appearance details
                    Map<String, Object> appearance = character.getAppearance();
                    if (appearance != null) {
                        Object clothing = appearance.get("clothing");
                        if (clothing != null) sb.append("穿着").append(clothing).append("，");
                        Object features = appearance.get("features");
                        if (features != null) sb.append(features).append("，");
                    }
                }
                sb.append(String.format("  表情：%s\n", translateExpression(expression)));
            }
            sb.append("\n");
        }

        // 2. Background and location
        if (frame.getBgDescription() != null && !frame.getBgDescription().isBlank()) {
            sb.append("【场景背景】\n").append(frame.getBgDescription()).append("\n\n");
        }

        // 3. Shot composition
        sb.append(String.format("【镜头】\n%s，%s机位\n\n",
                translateShotType(frame.getShotType()),
                frame.getCameraAngle() != null ? translateCameraAngle(frame.getCameraAngle()) : "平视"));

        // 4. Dialogue
        if (frame.getSubtitleText() != null && !frame.getSubtitleText().isBlank()) {
            sb.append("【台词口型同步】\n'").append(frame.getSubtitleText()).append("'\n\n");
        }

        // 5. Action/motion description
        sb.append("【动作描述】\n");
        sb.append(buildMotionDescription(frame.getShotType(),
                frameChars != null ? frameChars : List.of()));
        sb.append("\n\n");

        // 6. Style specification
        sb.append(String.format(
                "【风格要求】\n红果剧场漫剧风格，日系动画质感，柔和光影，%s赛道，9:16竖屏",
                projectTrack != null ? projectTrack : "都市甜宠"));

        return sb.toString();
    }

    /**
     * Build a video generation prompt with extra context (for retries).
     */
    public String buildRetryPrompt(String originalPrompt, String feedback) {
        return originalPrompt + "\n\n修正要求：" + feedback;
    }

    private Character findCharacter(List<Character> characters, String name) {
        return characters.stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private String translateExpression(String expr) {
        if (expr == null) return "自然";
        return switch (expr.toLowerCase()) {
            case "neutral" -> "自然平静";
            case "happy" -> "开心微笑";
            case "sad" -> "悲伤难过";
            case "surprised" -> "惊讶";
            case "angry" -> "生气愤怒";
            default -> expr;
        };
    }

    private String translateShotType(String shotType) {
        if (shotType == null) return "中景(MS)";
        return switch (shotType.toUpperCase()) {
            case "ECU" -> "极致特写(ECU)";
            case "CU" -> "特写(CU)";
            case "MCU" -> "中近景(MCU)";
            case "MS" -> "中景(MS)";
            case "FS" -> "全景(FS)";
            case "LS" -> "远景(LS)";
            default -> shotType;
        };
    }

    private String translateCameraAngle(String angle) {
        if (angle == null) return "平视";
        return switch (angle) {
            case "俯视" -> "俯视";
            case "仰视" -> "仰视";
            default -> "平视";
        };
    }

    private String buildMotionDescription(String shotType,
                                          List<Map<String, Object>> frameChars) {
        // Check if any character has an extreme expression that needs action
        boolean hasSurprised = frameChars.stream()
                .anyMatch(c -> "surprised".equals(c.get("expression")));
        boolean hasAngry = frameChars.stream()
                .anyMatch(c -> "angry".equals(c.get("expression")));

        StringBuilder motion = new StringBuilder();
        motion.append("动作：");

        if (hasSurprised) {
            motion.append("轻微身体后仰(惊讶反应)，");
        } else if (hasAngry) {
            motion.append("身体微微前倾(情绪激动)，");
        }

        motion.append("自然呼吸起伏");

        // Add camera motion based on shot type
        if (shotType != null) {
            switch (shotType.toUpperCase()) {
                case "CU", "ECU" ->
                    motion.append("，镜头极缓慢推进(增强情绪张力)");
                case "FS", "LS" ->
                    motion.append("，镜头缓慢横摇(展示场景空间)");
                default ->
                    motion.append("，镜头微动(避免完全静止)");
            }
        }

        motion.append("。");
        return motion.toString();
    }
}
