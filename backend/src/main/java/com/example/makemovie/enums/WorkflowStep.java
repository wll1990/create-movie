package com.example.makemovie.enums;

import lombok.Getter;

@Getter
public enum WorkflowStep {
    TOPIC_DESIGN("选题构思"),
    SCRIPT_CREATION("剧本创作"),
    CHARACTER_DESIGN("人设设计"),
    STORYBOARD_DESIGN("分镜设计"),
    VOICE_GENERATION("配音生成"),
    CLIP_GENERATION("视频片段生成"),
    FINAL_COMPOSITION("最终合成"),
    COPYWRITING("文案发布");

    private final String displayName;

    WorkflowStep(String displayName) {
        this.displayName = displayName;
    }
}
