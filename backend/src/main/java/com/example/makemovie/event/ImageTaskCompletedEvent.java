package com.example.makemovie.event;

import com.example.makemovie.entity.ImageGenTask;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published by ImageGenPoller when an image generation task completes or fails.
 * CharacterImageService listens for this to update character entities.
 */
@Getter
public class ImageTaskCompletedEvent extends ApplicationEvent {
    private final ImageGenTask task;
    private final String persistentUrl;

    public ImageTaskCompletedEvent(Object source, ImageGenTask task, String persistentUrl) {
        super(source);
        this.task = task;
        this.persistentUrl = persistentUrl;
    }
}
