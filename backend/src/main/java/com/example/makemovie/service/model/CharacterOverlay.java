package com.example.makemovie.service.model;

import java.util.UUID;

/**
 * Character image overlay on a storyboard frame.
 * Position uses normalized coordinates (0.0-1.0).
 */
public record CharacterOverlay(
        UUID characterId,
        String characterName,
        String expression,
        String imagePath,
        String imageUrl,
        double x,
        double y,
        double scale,
        int layer
) {}
