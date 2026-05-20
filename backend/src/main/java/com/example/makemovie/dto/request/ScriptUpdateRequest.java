package com.example.makemovie.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class ScriptUpdateRequest {

    @NotBlank(message = "剧本标题不能为空")
    private String title;

    @NotBlank(message = "剧本内容不能为空")
    private Map<String, Object> content;
}
