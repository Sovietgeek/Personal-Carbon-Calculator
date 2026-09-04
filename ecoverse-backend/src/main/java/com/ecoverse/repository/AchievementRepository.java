package com.ecoverse.repository;

import com.ecoverse.model.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AchievementRepository extends JpaRepository<Achievement, Long> {

    List<Achievement> findByCategory(String category);

    Optional<Achievement> findByCode(String code);
}
