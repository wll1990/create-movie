package com.example.makemovie.service;

import com.example.makemovie.service.model.TimelineSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates transition effects between timeline segments.
 * Outputs FFmpeg xfade/fade filter parameters.
 */
@Slf4j
@Service
public class TransitionService {

    private static final double DEFAULT_FADE_DURATION = 0.3; // seconds

    /**
     * Apply transition effects to a list of timeline segments.
     * For "cut" transitions: no effect added.
     * For "fade" transitions: adds xfade fade effect between adjacent segments.
     *
     * @return List of FFmpeg xfade filter strings for use in filter_complex
     */
    public List<String> buildTransitionFilters(List<TimelineSegment> segments) {
        List<String> filters = new ArrayList<>();

        for (int i = 1; i < segments.size(); i++) {
            TimelineSegment prev = segments.get(i - 1);
            TimelineSegment curr = segments.get(i);
            String transition = curr.transition();

            if ("fade".equalsIgnoreCase(transition)) {
                // xfade=fade:duration=0.3:offset=prev_end_time-0.3
                filters.add(String.format(
                        "[v%d][v%d]xfade=fade:duration=%.1f:offset=%.1f[v%d_t]",
                        i - 1, i, DEFAULT_FADE_DURATION,
                        prev.totalDurationSec() - DEFAULT_FADE_DURATION, i
                ));
            } else {
                // cut: concat without effect, handled by concat filter
                filters.add(String.format("[v%d][v%d]concat[v%d_t]", i - 1, i, i));
            }
        }

        return filters;
    }
}
