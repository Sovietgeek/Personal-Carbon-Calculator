package com.ecoverse.service.carbon;

import com.ecoverse.util.UnitConverter;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Unit Converter")
class UnitConverterTest {

    // ===== Distance Conversions =====

    @Nested
    @DisplayName("Distance → km")
    class DistanceToKm {

        @Test
        @DisplayName("1 km = 1 km")
        void kmToKm() {
            assertThat(UnitConverter.toKm(new BigDecimal("1"), "km"))
                    .isEqualByComparingTo("1.0000");
        }

        @Test
        @DisplayName("1 mile = 1.60934 km")
        void mileToKm() {
            assertThat(UnitConverter.toKm(new BigDecimal("1"), "mi"))
                    .isEqualByComparingTo("1.6093");
        }

        @Test
        @DisplayName("1000 m = 1 km")
        void metersToKm() {
            assertThat(UnitConverter.toKm(new BigDecimal("1000"), "m"))
                    .isEqualByComparingTo("1.0000");
        }

        @Test
        @DisplayName("Unsupported unit throws exception")
        void unsupportedUnit() {
            assertThatThrownBy(() -> UnitConverter.toKm(new BigDecimal("1"), "lightyears"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported distance unit");
        }

        @Test
        @DisplayName("Case-insensitive unit matching")
        void caseInsensitive() {
            assertThat(UnitConverter.toKm(new BigDecimal("1"), "KM"))
                    .isEqualByComparingTo("1.0000");
            assertThat(UnitConverter.toKm(new BigDecimal("1"), "Mi"))
                    .isEqualByComparingTo("1.6093");
        }
    }

    // ===== Energy Conversions =====

    @Nested
    @DisplayName("Energy → kWh")
    class EnergyToKwh {

        @Test
        @DisplayName("1 kWh = 1 kWh")
        void kwhToKwh() {
            assertThat(UnitConverter.toKwh(new BigDecimal("1"), "kwh"))
                    .isEqualByComparingTo("1.0000");
        }

        @Test
        @DisplayName("1000 Wh = 1 kWh")
        void whToKwh() {
            assertThat(UnitConverter.toKwh(new BigDecimal("1000"), "wh"))
                    .isEqualByComparingTo("1.0000");
        }

        @Test
        @DisplayName("1 MWh = 1000 kWh")
        void mwhToKwh() {
            assertThat(UnitConverter.toKwh(new BigDecimal("1"), "mwh"))
                    .isEqualByComparingTo("1000.0000");
        }
    }

    // ===== Mass Conversions =====

    @Nested
    @DisplayName("Mass → kg")
    class MassToKg {

        @Test
        @DisplayName("1 kg = 1 kg")
        void kgToKg() {
            assertThat(UnitConverter.toKg(new BigDecimal("1"), "kg"))
                    .isEqualByComparingTo("1.0000");
        }

        @Test
        @DisplayName("1000 g = 1 kg")
        void gramsToKg() {
            assertThat(UnitConverter.toKg(new BigDecimal("1000"), "g"))
                    .isEqualByComparingTo("1.0000");
        }

        @Test
        @DisplayName("1 tonne = 1000 kg")
        void tonnesToKg() {
            assertThat(UnitConverter.toKg(new BigDecimal("1"), "tonnes"))
                    .isEqualByComparingTo("1000.0000");
        }
    }

    // ===== Unit Detection =====

    @Nested
    @DisplayName("Unit Type Detection")
    class UnitTypeDetection {

        @Test
        @DisplayName("isDistanceUnit detects km, mi, m")
        void distanceUnits() {
            assertThat(UnitConverter.isDistanceUnit("km")).isTrue();
            assertThat(UnitConverter.isDistanceUnit("mi")).isTrue();
            assertThat(UnitConverter.isDistanceUnit("m")).isTrue();
            assertThat(UnitConverter.isDistanceUnit("kg")).isFalse();
        }

        @Test
        @DisplayName("isEnergyUnit detects kwh, wh, mwh")
        void energyUnits() {
            assertThat(UnitConverter.isEnergyUnit("kwh")).isTrue();
            assertThat(UnitConverter.isEnergyUnit("wh")).isTrue();
            assertThat(UnitConverter.isEnergyUnit("mwh")).isTrue();
            assertThat(UnitConverter.isEnergyUnit("km")).isFalse();
        }

        @Test
        @DisplayName("isMassUnit detects kg, g, tonnes")
        void massUnits() {
            assertThat(UnitConverter.isMassUnit("kg")).isTrue();
            assertThat(UnitConverter.isMassUnit("g")).isTrue();
            assertThat(UnitConverter.isMassUnit("tonnes")).isTrue();
            assertThat(UnitConverter.isMassUnit("kwh")).isFalse();
        }

        @Test
        @DisplayName("null and empty are not valid units")
        void nullAndEmptyNotValid() {
            assertThat(UnitConverter.isDistanceUnit(null)).isFalse();
            assertThat(UnitConverter.isEnergyUnit("")).isFalse();
            assertThat(UnitConverter.isMassUnit(null)).isFalse();
        }
    }

    // ===== Canonical Unit =====

    @Nested
    @DisplayName("Canonical Unit Resolution")
    class CanonicalUnit {

        @Test
        @DisplayName("Transport canonical unit = km")
        void transportKm() {
            assertThat(UnitConverter.getCanonicalUnit("transport")).isEqualTo("km");
        }

        @Test
        @DisplayName("Energy canonical unit = kWh")
        void energyKwh() {
            assertThat(UnitConverter.getCanonicalUnit("energy")).isEqualTo("kwh");
        }

        @Test
        @DisplayName("Food canonical unit = meal")
        void foodMeal() {
            assertThat(UnitConverter.getCanonicalUnit("food")).isEqualTo("meal");
        }

        @Test
        @DisplayName("Shopping canonical unit = kg")
        void shoppingKg() {
            assertThat(UnitConverter.getCanonicalUnit("shopping")).isEqualTo("kg");
        }

        @Test
        @DisplayName("Waste canonical unit = kg")
        void wasteKg() {
            assertThat(UnitConverter.getCanonicalUnit("waste")).isEqualTo("kg");
        }

        @Test
        @DisplayName("Digital canonical unit = hr")
        void digitalHr() {
            assertThat(UnitConverter.getCanonicalUnit("digital")).isEqualTo("hr");
        }

        @Test
        @DisplayName("Unknown category returns null")
        void unknownNull() {
            assertThat(UnitConverter.getCanonicalUnit("unknown")).isNull();
        }
    }
}
