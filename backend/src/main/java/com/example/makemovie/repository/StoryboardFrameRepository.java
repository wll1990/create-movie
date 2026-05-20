package com.example.makemovie.repository;

import com.example.makemovie.entity.StoryboardFrame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoryboardFrameRepository extends JpaRepository<StoryboardFrame, UUID> {
    List<StoryboardFrame> findByStoryboardIdOrderByFrameNumber(UUID storyboardId);
}
