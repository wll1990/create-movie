package com.example.makemovie.service;

import com.example.makemovie.entity.Episode;
import com.example.makemovie.exception.BusinessException;
import com.example.makemovie.repository.EpisodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EpisodeService {

    private final EpisodeRepository episodeRepository;

    @Transactional
    public Episode createEpisode(UUID projectId, Integer number, String title) {
        if (title == null || title.isBlank()) {
            title = "第" + number + "集";
        }
        Episode episode = Episode.builder()
                .projectId(projectId)
                .episodeNumber(number)
                .title(title)
                .status("DRAFT")
                .build();
        episode = episodeRepository.save(episode);
        log.info("Episode created: projectId={}, number={}, title={}", projectId, number, title);
        return episode;
    }

    @Transactional
    public Episode createNextEpisode(UUID projectId) {
        long count = episodeRepository.countByProjectId(projectId);
        int nextNumber = (int) count + 1;
        return createEpisode(projectId, nextNumber, "第" + nextNumber + "集");
    }

    public Episode getEpisode(UUID episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> new BusinessException("EPISODE_NOT_FOUND", "集不存在"));
    }

    public List<Episode> listEpisodes(UUID projectId) {
        return episodeRepository.findByProjectIdOrderByEpisodeNumber(projectId);
    }

    public Episode getCurrentEpisode(UUID projectId) {
        List<Episode> episodes = episodeRepository.findByProjectIdOrderByEpisodeNumber(projectId);
        if (episodes.isEmpty()) {
            return createEpisode(projectId, 1, "第1集");
        }
        // Return the first non-COMPLETED episode, or the last one
        return episodes.stream()
                .filter(e -> !"COMPLETED".equals(e.getStatus()))
                .findFirst()
                .orElse(episodes.get(episodes.size() - 1));
    }

    public long getEpisodeCount(UUID projectId) {
        return episodeRepository.countByProjectId(projectId);
    }
}
