package com.ecoverse.repository;

import com.ecoverse.model.CarbonEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CarbonEntryRepository extends JpaRepository<CarbonEntry, Long> {

    List<CarbonEntry> findByUserId(Long userId);

    Page<CarbonEntry> findByUserIdOrderByEntryDateDesc(Long userId, Pageable pageable);

    List<CarbonEntry> findByUserIdAndEntryDateBetween(Long userId, Instant start, Instant end);

    Page<CarbonEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(Long userId, Instant start, Instant end, Pageable pageable);

    List<CarbonEntry> findByUserIdAndCategory(Long userId, String category);

    @Modifying
    @Query("DELETE FROM CarbonEntry c WHERE c.id = :id AND c.userId = :userId")
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    // ===== Period SUM queries (BigDecimal, timezone-aware via Instant) =====

    @Query("SELECT SUM(c.co2) FROM CarbonEntry c WHERE c.userId = :userId AND c.entryDate >= :start AND c.entryDate < :end AND c.calculationType = 'EMISSION'")
    BigDecimal sumEmissionsByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    @Query("SELECT SUM(c.co2) FROM CarbonEntry c WHERE c.userId = :userId AND c.entryDate >= :start AND c.entryDate < :end AND c.calculationType = 'AVOIDED_EMISSION'")
    BigDecimal sumAvoidedByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    /**
     * Sum all CO2 (both emission and avoided) for a period.
     * Used for backwards-compatible summary that treats all CO2 as one number.
     */
    @Query("SELECT SUM(c.co2) FROM CarbonEntry c WHERE c.userId = :userId AND c.entryDate >= :start AND c.entryDate < :end")
    BigDecimal sumCo2ByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    // ===== Lifetime aggregates (replaces loading ALL entries into memory) =====

    @Query("SELECT COALESCE(SUM(c.co2), 0) FROM CarbonEntry c WHERE c.userId = :userId AND c.calculationType = 'EMISSION'")
    BigDecimal sumTotalEmissionsByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(c.co2), 0) FROM CarbonEntry c WHERE c.userId = :userId AND c.calculationType = 'AVOIDED_EMISSION'")
    BigDecimal sumTotalAvoidedByUserId(@Param("userId") Long userId);

    // ===== Category breakdown (DB aggregate) =====

    @Query("SELECT c.category, SUM(c.co2) FROM CarbonEntry c WHERE c.userId = :userId AND c.calculationType = 'EMISSION' GROUP BY c.category")
    List<Object[]> categoryEmissionBreakdownByUserId(@Param("userId") Long userId);

    @Query("SELECT c.category, c.calculationType, SUM(c.co2) FROM CarbonEntry c WHERE c.userId = :userId GROUP BY c.category, c.calculationType")
    List<Object[]> categoryBreakdownWithTypeByUserId(@Param("userId") Long userId);

    // ===== Trend data (daily aggregates for chart) =====

    /**
     * Daily emission totals for a period, used by the dashboard trend chart.
     * Returns Object[] with [date_string, total_co2_emissions].
     */
    @Query("SELECT CAST(c.entryDate AS date), SUM(c.co2) FROM CarbonEntry c " +
           "WHERE c.userId = :userId AND c.entryDate >= :start AND c.entryDate < :end " +
           "AND c.calculationType = 'EMISSION' GROUP BY CAST(c.entryDate AS date) ORDER BY CAST(c.entryDate AS date)")
    List<Object[]> dailyEmissionsByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    /**
     * Daily avoided-emission totals for a period, used by the dashboard trend chart.
     * Returns Object[] with [date_string, total_co2_avoided].
     */
    @Query("SELECT CAST(c.entryDate AS date), SUM(c.co2) FROM CarbonEntry c " +
           "WHERE c.userId = :userId AND c.entryDate >= :start AND c.entryDate < :end " +
           "AND c.calculationType = 'AVOIDED_EMISSION' GROUP BY CAST(c.entryDate AS date) ORDER BY CAST(c.entryDate AS date)")
    List<Object[]> dailyAvoidedByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    // ===== Existence checks (for streak and achievements) =====

    /**
     * Check if a user has any carbon entry in a given period.
     */
    @Query("SELECT COUNT(c) > 0 FROM CarbonEntry c WHERE c.userId = :userId AND c.entryDate >= :start AND c.entryDate < :end")
    boolean existsByUserIdAndPeriod(@Param("userId") Long userId, @Param("start") Instant start, @Param("end") Instant end);

    // ===== ADMIN AGGREGATE QUERIES =====

    @Query("SELECT COALESCE(SUM(c.co2), 0) FROM CarbonEntry c WHERE c.calculationType = 'EMISSION'")
    BigDecimal sumTotalEmissions();

    @Query("SELECT c.category, SUM(c.co2) FROM CarbonEntry c WHERE c.calculationType = 'EMISSION' GROUP BY c.category")
    List<Object[]> categoryEmissionBreakdown();

    @Query("SELECT CAST(c.entryDate AS date), SUM(c.co2) FROM CarbonEntry c " +
           "WHERE c.entryDate >= :start AND c.entryDate < :end AND c.calculationType = 'EMISSION' " +
           "GROUP BY CAST(c.entryDate AS date) ORDER BY CAST(c.entryDate AS date)")
    List<Object[]> dailyEmissionsByPeriod(@Param("start") Instant start, @Param("end") Instant end);

    long countByUserId(Long userId);
}
