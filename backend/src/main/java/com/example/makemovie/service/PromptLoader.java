package com.example.makemovie.service;

import com.example.makemovie.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads prompt templates from classpath and fills in placeholders.
 *
 * Prompt files live in src/main/resources/prompts/
 * Edit them without recompiling (just restart).
 *
 * Placeholders use ${key} syntax, e.g.:
 *   "赛道：${track}"
 */
@Slf4j
@Component
public class PromptLoader {

    private static final String PROMPTS_PATH = "prompts/";
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Load a prompt template and fill placeholders.
     *
     * @param templateName e.g. "script-system", "character-design"
     * @param vars         placeholder → value mappings
     * @return filled prompt string
     */
    public String load(String templateName, Map<String, String> vars) {
        String template = cache.computeIfAbsent(templateName, this::readFile);
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("${" + e.getKey() + "}", e.getValue());
        }
        return result;
    }

    /**
     * Reload all cached templates (for hot-reload during development).
     */
    public void reload() {
        cache.clear();
        log.info("Prompt templates reloaded");
    }

    private String readFile(String name) {
        String path = PROMPTS_PATH + name + ".txt";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new BusinessException("PROMPT_NOT_FOUND", "提示词文件不存在: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("PROMPT_LOAD_FAILED",
                    "无法加载提示词: " + path + " - " + e.getMessage());
        }
    }
}
