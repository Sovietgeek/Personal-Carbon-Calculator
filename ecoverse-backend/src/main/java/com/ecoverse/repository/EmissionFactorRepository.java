package com.ecoverse.repository;

import com.ecoverse.model.EmissionFactor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, Long> {

    List<EmissionFactor> findByCategory(String category);

    Optional<EmissionFactor> findByCategoryAndType(String category, String type);

    /**
     * Find the active factor for a given category and type.
     * Used for carbon calculations — always uses the latest active version.
     */
    Optional<EmissionFactor> findByCategoryAndTypeAndActiveTrue(String category, String type);

    /**
     * Find all active factors for a given category.
     * Used for listing available types per category.
     */
    List<EmissionFactor> findByCategoryAndActiveTrue(String category);

    /**
     * Find all active factors for a given category, ordered by type.
     */
    List<EmissionFactor> findByCategoryAndActiveTrueOrderByType(String category);

    /**
     * Find a specific version of a factor.
     * Used for historical reference and immutability checks.
     */
    Optional<EmissionFactor> findByCategoryAndTypeAndVersion(String category, String type, Integer version);

    /**
     * Find all versions of a factor (including inactive).
     * Used for factor management and auditing.
     */
    List<EmissionFactor> findByCategoryAndTypeOrderByVersionDesc(String category, String type);
}
