package com.example.makemovie.service.model;

import java.util.List;
import java.util.UUID;

/**
 * Represents one segment in the composition timeline.
 * Each segment = one storyboard frame rendered with audio + visuals.
 */
public record TimelineSegment(
        int index,
        UUID storyboardFrameId,
        UUID sceneId,
        String bgImagePath,
        String bgImageUrl,
        List<CharacterOverlay> characters,
        String audioPath,
        double audioDurationSec,
        double paddingBeforeSec,
        double paddingAfterSec,
        double totalDurationSec,
        String subtitleText,
        String transition,
        String shotType
) {
    public double startTimeSec() {
        return 0; // calculated during assembly
    }
}
