package com.ecoverse.service.carbon;

import com.ecoverse.dto.carbon.CarbonEntryRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.service.CarbonEntryValidator;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Carbon Entry Validation")
class CarbonEntryValidationTest {

    private CarbonEntryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CarbonEntryValidator();
    }

    // ===== Category Validation =====

    @Nested
    @DisplayName("Category Validation")
    class CategoryValidation {

        @Test
        @DisplayName("Valid categories are accepted with proper fields")
        void validCategories() {
            // Transport
            CarbonEntryRequest transport = makeRequest("transport", "car-petrol", null);
            transport.setDistance(new BigDecimal("50"));
            transport.setDistanceUnit("km");
            assertThatCode(() -> validator.validate(transport)).doesNotThrowAnyException();

            // Energy
            CarbonEntryRequest energy = makeRequest("energy", "electricity", null);
            energy.setConsumption(new BigDecimal("10"));
            assertThatCode(() -> validator.validate(energy)).doesNotThrowAnyException();

            // Food
            CarbonEntryRequest food = makeRequest("food", "beef", null);
            food.setMeals(new BigDecimal("1"));
            assertThatCode(() -> validator.validate(food)).doesNotThrowAnyException();

            // Shopping
            CarbonEntryRequest shopping = makeRequest("shopping", "clothing-kg", new BigDecimal("1"));
            assertThatCode(() -> validator.validate(shopping)).doesNotThrowAnyException();

            // Waste
            CarbonEntryRequest waste = makeRequest("waste", "landfill", new BigDecimal("1"));
            assertThatCode(() -> validator.validate(waste)).doesNotThrowAnyException();

            // Digital
            CarbonEntryRequest digital = makeRequest("digital", "streaming-hd", new BigDecimal("1"));
            assertThatCode(() -> validator.validate(digital)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Invalid category throws BadRequestException")
        void invalidCategory() {
            CarbonEntryRequest req = makeRequest("invalid", "any", new BigDecimal("1"));
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unknown emission category");
        }

        @Test
        @DisplayName("Null category throws BadRequestException")
        void nullCategory() {
            CarbonEntryRequest req = new CarbonEntryRequest();
            req.setType("car-petrol");
            req.setDistance(new BigDecimal("10"));
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Category is required");
        }

        @Test
        @DisplayName("Empty category throws BadRequestException")
        void emptyCategory() {
            CarbonEntryRequest req = makeRequest("", "any", new BigDecimal("1"));
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Category is required");
        }
    }

    // ===== Transport Validation =====

    @Nested
    @DisplayName("Transport Validation")
    class TransportValidation {

        @Test
        @DisplayName("Valid transport entry passes")
        void validTransport() {
            CarbonEntryRequest req = makeTransportRequest(new BigDecimal("50"), "km", 1);
            assertThatCode(() -> validator.validate(req)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Missing distance throws exception")
        void missingDistance() {
            CarbonEntryRequest req = makeRequest("transport", "car-petrol", null);
            req.setPassengers(1);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Distance is required");
        }

        @Test
        @DisplayName("Zero distance throws exception")
        void zeroDistance() {
            CarbonEntryRequest req = makeTransportRequest(BigDecimal.ZERO, "km", 1);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("positive");
        }

        @Test
        @DisplayName("Excessive distance throws exception")
        void excessiveDistance() {
            CarbonEntryRequest req = makeTransportRequest(new BigDecimal("60000"), "km", 1);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("Zero passengers throws exception")
        void zeroPassengers() {
            CarbonEntryRequest req = makeTransportRequest(new BigDecimal("50"), "km", 0);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("at least 1");
        }

        @Test
        @DisplayName("51 passengers throws exception (max 50)")
        void tooManyPassengers() {
            CarbonEntryRequest req = makeTransportRequest(new BigDecimal("50"), "km", 51);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }

        @Test
        @DisplayName("Invalid distance unit throws exception")
        void invalidDistanceUnit() {
            CarbonEntryRequest req = makeTransportRequest(new BigDecimal("50"), "lightyears", 1);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unsupported distance unit");
        }
    }

    // ===== Energy Validation =====

    @Nested
    @DisplayName("Energy Validation")
    class EnergyValidation {

        @Test
        @DisplayName("Missing consumption throws exception")
        void missingConsumption() {
            CarbonEntryRequest req = makeRequest("energy", "electricity", null);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Consumption is required");
        }

        @Test
        @DisplayName("Excessive energy throws exception")
        void excessiveEnergy() {
            CarbonEntryRequest req = makeRequest("energy", "electricity", new BigDecimal("2000000"));
            req.setConsumption(new BigDecimal("2000000"));
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }
    }

    // ===== Food Validation =====

    @Nested
    @DisplayName("Food Validation")
    class FoodValidation {

        @Test
        @DisplayName("Missing meals throws exception")
        void missingMeals() {
            CarbonEntryRequest req = makeRequest("food", "beef", null);
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("meals");
        }

        @Test
        @DisplayName("Excessive meals throws exception")
        void excessiveMeals() {
            CarbonEntryRequest req = makeRequest("food", "beef", new BigDecimal("200"));
            req.setMeals(new BigDecimal("200"));
            assertThatThrownBy(() -> validator.validate(req))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum");
        }
    }

    // ===== Helpers =====

    private CarbonEntryRequest makeRequest(String category, String type, BigDecimal quantity) {
        CarbonEntryRequest req = new CarbonEntryRequest();
        req.setCategory(category);
        req.setType(type);
        req.setQuantity(quantity);
        return req;
    }

    private CarbonEntryRequest makeTransportRequest(BigDecimal distance, String unit, int passengers) {
        CarbonEntryRequest req = new CarbonEntryRequest();
        req.setCategory("transport");
        req.setType("car-petrol");
        req.setDistance(distance);
        req.setDistanceUnit(unit);
        req.setPassengers(passengers);
        return req;
    }
}
