package com.example.makemovie.repository;

import com.example.makemovie.entity.Material;
import com.example.makemovie.enums.MaterialType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MaterialRepository extends JpaRepository<Material, UUID> {
    List<Material> findByType(MaterialType type);
    List<Material> findByCategory(String category);
    List<Material> findByProjectIdOrProjectIdIsNull(UUID projectId);
    List<Material> findBySource(String source);

    @Query(value = "SELECT * FROM material WHERE :tag = ANY(tags)", nativeQuery = true)
    List<Material> findByTag(String tag);
}
