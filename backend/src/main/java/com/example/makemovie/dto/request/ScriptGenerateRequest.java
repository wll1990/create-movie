package com.example.makemovie.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ScriptGenerateRequest {

    @NotBlank(message = "赛道不能为空")
    private String track;

    @NotBlank(message = "主题不能为空")
    private String theme;

    @Min(value = 15, message = "时长最少15秒")
    @Max(value = 180, message = "时长最多180秒")
    private Integer duration = 45;

    private String templateId;

    private Map<String, Boolean> templateInheritance;

    private List<CharacterInput> characters;

    @Data
    public static class CharacterInput {
        private String name;
        private String role;
        private String personality;
    }
}
