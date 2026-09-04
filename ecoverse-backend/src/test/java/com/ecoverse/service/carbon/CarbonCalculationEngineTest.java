package com.ecoverse.service.carbon;

import com.ecoverse.model.CalculationType;
import com.ecoverse.model.EmissionFactor;
import com.ecoverse.service.CarbonCalculationEngine;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Carbon Calculation Engine")
class CarbonCalculationEngineTest {

    private CarbonCalculationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new CarbonCalculationEngine();
    }

    // ===== Basic Calculation =====

    @Nested
    @DisplayName("Basic Calculation")
    class BasicCalculation {

        @Test
        @DisplayName("factor × input = correct CO2")
        void factorTimesInput() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("100"), null, null, null);
            assertThat(co2).isEqualByComparingTo("21.0000");
        }

        @Test
        @DisplayName("Zero input value throws exception")
        void zeroInputThrows() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            assertThatThrownBy(() -> engine.calculate(factor, BigDecimal.ZERO, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("Negative input value throws exception")
        void negativeInputThrows() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            assertThatThrownBy(() -> engine.calculate(factor, new BigDecimal("-5"), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Null factor throws exception")
        void nullFactorThrows() {
            assertThatThrownBy(() -> engine.calculate(null, new BigDecimal("100"), null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("factor");
        }

        @Test
        @DisplayName("Null input value throws exception")
        void nullInputThrows() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            assertThatThrownBy(() -> engine.calculate(factor, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ===== Passengers =====

    @Nested
    @DisplayName("Passenger Division")
    class PassengerDivision {

        @Test
        @DisplayName("1 passenger = full CO2")
        void onePassenger() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("100"), 1, null, null);
            assertThat(co2).isEqualByComparingTo("21.0000");
        }

        @Test
        @DisplayName("4 passengers = CO2/4")
        void fourPassengers() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("100"), 4, null, null);
            assertThat(co2).isEqualByComparingTo("5.2500");
        }

        @Test
        @DisplayName("null passengers = 1 passenger (default)")
        void nullPassengersDefaultsToOne() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("100"), null, null, null);
            assertThat(co2).isEqualByComparingTo("21.0000");
        }
    }

    // ===== Modifiers =====

    @Nested
    @DisplayName("Modifiers (Secondhand)")
    class Modifiers {

        @Test
        @DisplayName("Secondhand modifier = ×0.5")
        void secondhandModifier() {
            EmissionFactor factor = makeFactor("shopping", "clothing-kg", "15.000000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("2"), null, "secondhand", new BigDecimal("0.5"));
            assertThat(co2).isEqualByComparingTo("15.0000");
        }

        @Test
        @DisplayName("No modifier = full CO2")
        void noModifier() {
            EmissionFactor factor = makeFactor("shopping", "clothing-kg", "15.000000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("2"), null, null, null);
            assertThat(co2).isEqualByComparingTo("30.0000");
        }

        @Test
        @DisplayName("Modifier > 1 is ignored (invalid)")
        void modifierGreaterThanOneIgnored() {
            EmissionFactor factor = makeFactor("shopping", "clothing-kg", "15.000000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("2"), null, "invalid", new BigDecimal("1.5"));
            assertThat(co2).isEqualByComparingTo("30.0000"); // No modifier applied
        }

        @Test
        @DisplayName("Modifier = 0 is ignored (invalid)")
        void zeroModifierIgnored() {
            EmissionFactor factor = makeFactor("shopping", "clothing-kg", "15.000000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("2"), null, "invalid", BigDecimal.ZERO);
            assertThat(co2).isEqualByComparingTo("30.0000");
        }
    }

    // ===== Rounding =====

    @Nested
    @DisplayName("Rounding and Precision")
    class Rounding {

        @Test
        @DisplayName("CO2 rounded to 4 decimal places (HALF_UP)")
        void roundingFourDecimalPlaces() {
            EmissionFactor factor = makeFactor("transport", "car-petrol", "0.210000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("33"), null, null, null);
            // 0.21 × 33 = 6.93
            assertThat(co2).isEqualByComparingTo("6.9300");
            assertThat(co2.scale()).isEqualTo(4);
        }

        @Test
        @DisplayName("Very small values preserved with 4 decimal precision")
        void verySmallValues() {
            EmissionFactor factor = makeFactor("digital", "ai-query", "0.002000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("1"), null, null, null);
            assertThat(co2).isEqualByComparingTo("0.0020");
        }

        @Test
        @DisplayName("Large values calculated correctly")
        void largeValues() {
            EmissionFactor factor = makeFactor("digital", "crypto-transaction", "25.000000");
            BigDecimal co2 = engine.calculate(factor, new BigDecimal("100"), null, null, null);
            assertThat(co2).isEqualByComparingTo("2500.0000");
        }
    }

    // ===== CalculationType Determination =====

    @Nested
    @DisplayName("CalculationType Determination")
    class CalculationTypeDetermination {

        @Test
        @DisplayName("Transport = EMISSION")
        void transportIsEmission() {
            assertThat(engine.determineCalculationType("transport", "car-petrol"))
                    .isEqualTo(CalculationType.EMISSION);
        }

        @Test
        @DisplayName("Energy solar = AVOIDED_EMISSION")
        void solarIsAvoided() {
            assertThat(engine.determineCalculationType("energy", "solar"))
                    .isEqualTo(CalculationType.AVOIDED_EMISSION);
        }

        @Test
        @DisplayName("Waste recycled = AVOIDED_EMISSION")
        void recycledIsAvoided() {
            assertThat(engine.determineCalculationType("waste", "recycled"))
                    .isEqualTo(CalculationType.AVOIDED_EMISSION);
        }

        @Test
        @DisplayName("Waste composted = AVOIDED_EMISSION")
        void compostedIsAvoided() {
            assertThat(engine.determineCalculationType("waste", "composted"))
                    .isEqualTo(CalculationType.AVOIDED_EMISSION);
        }

        @Test
        @DisplayName("Waste landfill = EMISSION")
        void landfillIsEmission() {
            assertThat(engine.determineCalculationType("waste", "landfill"))
                    .isEqualTo(CalculationType.EMISSION);
        }

        @Test
        @DisplayName("Energy electricity = EMISSION")
        void electricityIsEmission() {
            assertThat(engine.determineCalculationType("energy", "electricity"))
                    .isEqualTo(CalculationType.EMISSION);
        }
    }

    // ===== Helper =====

    private EmissionFactor makeFactor(String category, String type, String factorValue) {
        return EmissionFactor.builder()
                .id(1L)
                .category(category)
                .type(type)
                .factor(new BigDecimal(factorValue))
                .unit("kg/km")
                .version(1)
                .active(true)
                .build();
    }
}
