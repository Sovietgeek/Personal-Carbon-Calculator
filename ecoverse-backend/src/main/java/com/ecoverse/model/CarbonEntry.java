package com.ecoverse.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "carbon_entries", indexes = {
    @Index(name = "idx_carbon_entry_user_id", columnList = "user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CarbonEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String type;

    /**
     * CO2 value in kg. Always non-negative. Direction is determined by calculationType:
     * - EMISSION: positive contribution to carbon footprint
     * - AVOIDED_EMISSION: avoided/offset amount (e.g., solar, recycling)
     * - CREDIT: carbon credit purchased
     */
    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal co2;

    @Column(name = "entry_date", nullable = false)
    private Instant entryDate;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // ===== Phase D: Production Carbon Engine fields =====

    /**
     * Activity type: the specific sub-type of the emission
     * (e.g., "car-petrol", "electricity", "beef").
     * For backward compatibility, this mirrors the 'type' column for existing entries.
     */
    @Column(name = "activity_type", length = 100)
    private String activityType;

    /**
     * Raw input value from the user (e.g., distance in km, consumption in kWh).
     * Stored for audit trail and recalculation capability.
     */
    @Column(name = "input_value", precision = 12, scale = 4)
    private BigDecimal inputValue;

    /**
     * Unit of the input value (e.g., "km", "kWh", "meal", "kg", "hr").
     */
    @Column(name = "input_unit", length = 20)
    private String inputUnit;

    /**
     * Reference to the emission factor used for this calculation.
     * NULL for entries created before Phase D.
     */
    @Column(name = "factor_id")
    private Long factorId;

    /**
     * Version of the emission factor at time of calculation.
     * Ensures historical immutability — if the factor is updated,
     * old entries still reference the correct version.
     */
    @Column(name = "factor_version")
    @Builder.Default
    private Integer factorVersion = 1;

    /**
     * Calculation type: EMISSION, AVOIDED_EMISSION, or CREDIT.
     * Replaces the old convention of negative CO2 values for savings.
     */
    @Column(name = "calculation_type", length = 20)
    @Builder.Default
    private String calculationType = "EMISSION";

    /**
     * Modifier type applied (e.g., "secondhand", "carpool").
     */
    @Column(name = "modifier_type", length = 50)
    private String modifierType;

    /**
     * Modifier multiplier value (e.g., 0.5 for secondhand).
     */
    @Column(name = "modifier_value", precision = 8, scale = 4)
    private BigDecimal modifierValue;

    /**
     * IANA timezone string at time of entry (e.g., "Asia/Kolkata").
     * Used for timezone-correct period calculations.
     */
    @Column(name = "user_timezone", length = 50)
    private String userTimezone;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        if (this.entryDate == null) {
            this.entryDate = Instant.now();
        }
    }
}
