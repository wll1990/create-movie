package com.example.makemovie.dto.request;

import com.example.makemovie.enums.ProjectMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProjectRequest {

    @NotBlank(message = "项目标题不能为空")
    private String title;

    private String track;

    @NotNull(message = "项目模式不能为空")
    private ProjectMode mode;

    private String theme;

    private String sourceVideoGeneId;
    private String creationTemplateId;
}
