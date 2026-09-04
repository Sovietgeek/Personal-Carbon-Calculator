package com.ecoverse.repository;

import com.ecoverse.model.AiUsageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    Page<AiUsageLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndSuccess(Long userId, boolean success);

    Optional<AiUsageLog> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    // ===== ADMIN QUERIES =====

    Page<AiUsageLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countBySuccess(boolean success);

    @Query("SELECT l.provider, COUNT(l) FROM AiUsageLog l GROUP BY l.provider")
    List<Object[]> countByProvider();

    @Query("SELECT CAST(l.createdAt AS date), COUNT(l) FROM AiUsageLog l " +
           "WHERE l.createdAt >= :since GROUP BY CAST(l.createdAt AS date) ORDER BY CAST(l.createdAt AS date)")
    List<Object[]> dailyCountSince(@Param("since") java.time.LocalDateTime since);
}
