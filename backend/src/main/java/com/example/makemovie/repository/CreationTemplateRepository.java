package com.example.makemovie.repository;

import com.example.makemovie.entity.CreationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreationTemplateRepository extends JpaRepository<CreationTemplate, UUID> {
    List<CreationTemplate> findByIsDefaultTrue();
}
