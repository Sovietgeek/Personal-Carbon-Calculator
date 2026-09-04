package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "emission_factors",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_emission_factor_category_type_version", columnNames = {"category", "type", "version"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmissionFactor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String type;

    /**
     * Emission factor value. Always non-negative.
     * For AVOIDED_EMISSION types (solar, recycled, composted),
     * the factor value is positive and the calculation_type on CarbonEntry
     * determines the direction.
     */
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal factor;

    @Column(nullable = false)
    private String unit;

    // ===== Phase D: Metadata and Verification =====

    /**
     * Human-readable name of the data source (e.g., "IPCC AR6", "DEFRA 2023").
     */
    @Column(name = "source_name")
    private String sourceName;

    /**
     * URL to the source document or dataset.
     */
    @Column(name = "source_url", columnDefinition = "TEXT")
    private String sourceUrl;

    /**
     * Date when this factor's value was last verified against the source.
     */
    @Column(name = "verification_date")
    private LocalDate verificationDate;

    /**
     * Version of this factor. Incremented when the factor value is updated.
     * Old versions are kept with active=false for historical reference.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * Whether this factor is currently active and should be used for new calculations.
     * Inactive factors are kept for historical reference (immutability).
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Date from which this factor value is effective.
     */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /**
     * Date until which this factor value is effective.
     * NULL means currently effective (no end date).
     */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /**
     * Geographic region this factor applies to (e.g., "IN", "GLOBAL", "EU").
     */
    @Column(length = 100)
    @Builder.Default
    private String region = "IN";

    /**
     * Human-readable description of the uncertainty range
     * (e.g., "±30% (varies by vehicle age, load, driving conditions)").
     */
    @Column
    private String uncertainty;

    /**
     * Verification status of this factor:
     * - VERIFIED: Peer-reviewed or from authoritative government source
     * - ESTIMATED: Derived from general data, not India-specific
     * - NOT_VERIFIED: No documented source, approximate value
     */
    @Column(name = "verification_status", length = 20)
    @Builder.Default
    private String verificationStatus = "NOT_VERIFIED";

    /**
     * The expected input unit for this factor (e.g., "km", "kWh", "meal", "kg", "hr").
     * Used for validation — the user's input unit must match or be convertible.
     */
    @Column(name = "input_unit", length = 20)
    private String inputUnit;
}
