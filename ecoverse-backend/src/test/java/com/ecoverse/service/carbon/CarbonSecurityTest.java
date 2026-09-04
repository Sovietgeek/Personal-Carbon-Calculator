package com.ecoverse.service.carbon;

import com.ecoverse.dto.carbon.CarbonEntryRequest;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.model.CalculationType;
import com.ecoverse.model.CarbonEntry;
import com.ecoverse.model.EmissionFactor;
import com.ecoverse.repository.CarbonEntryRepository;
import com.ecoverse.repository.EmissionFactorRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Carbon security tests: verify that the server is the authoritative source
 * for CO2 calculations and the client cannot override them.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Carbon Security")
class CarbonSecurityTest {

    @Mock private CarbonEntryRepository carbonEntryRepository;
    @Mock private EmissionFactorRepository emissionFactorRepository;
    @Mock private UserRepository userRepository;
    @Mock private CarbonEntryValidator entryValidator;
    @Mock private TimezoneService timezoneService;
    @Mock private StreakService streakService;

    @InjectMocks private CarbonService carbonService;

    private CarbonCalculationEngine calculationEngine;
    private RiskService riskService;

    @BeforeEach
    void setUp() {
        calculationEngine = new CarbonCalculationEngine();
        riskService = new RiskService();
        // Manually inject since @InjectMocks won't handle non-mock dependencies
        carbonService = new CarbonService();
        // Use reflection to inject dependencies
        injectField(carbonService, "carbonEntryRepository", carbonEntryRepository);
        injectField(carbonService, "emissionFactorRepository", emissionFactorRepository);
        injectField(carbonService, "userRepository", userRepository);
        injectField(carbonService, "calculationEngine", calculationEngine);
        injectField(carbonService, "entryValidator", entryValidator);
        injectField(carbonService, "timezoneService", timezoneService);
        injectField(carbonService, "riskService", riskService);
        injectField(carbonService, "streakService", streakService);
    }

    // ===== Client Cannot Send CO2 =====

    @Test
    @DisplayName("Client-sent CO2 is IGNORED — server calculates from factor + input")
    void clientSentCo2Ignored() {
        // CarbonEntryRequest no longer has a co2 field
        // This test verifies the request type has no co2 field
        CarbonEntryRequest req = new CarbonEntryRequest();
        assertThatThrownBy(() -> req.getClass().getMethod("getCo2"))
                .isInstanceOf(NoSuchMethodException.class);
    }

    @Test
    @DisplayName("Server calculates CO2 from emission factor + input value")
    void serverCalculatesFromFactor() {
        EmissionFactor factor = EmissionFactor.builder()
                .id(1L).category("transport").type("car-petrol")
                .factor(new BigDecimal("0.210000")).unit("kg/km")
                .version(1).active(true).build();

        when(emissionFactorRepository.findByCategoryAndTypeAndActiveTrue("transport", "car-petrol"))
                .thenReturn(Optional.of(factor));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(timezoneService.now()).thenReturn(java.time.Instant.now());
        when(carbonEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CarbonEntryRequest req = new CarbonEntryRequest();
        req.setCategory("transport");
        req.setType("car-petrol");
        req.setDistance(new BigDecimal("100"));
        req.setDistanceUnit("km");
        req.setPassengers(1);

        // The validator needs to pass
        doNothing().when(entryValidator).validate(any());

        var response = carbonService.addEntry(1L, req);

        // Server calculated: 0.21 × 100 = 21.0 kg
        assertThat(response.getCo2()).isEqualByComparingTo("21.0000");
        assertThat(response.getCalculationType()).isEqualTo("EMISSION");
        assertThat(response.getFactorId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Unknown emission type returns error (no fallback to hardcoded values)")
    void unknownTypeReturnsError() {
        when(emissionFactorRepository.findByCategoryAndTypeAndActiveTrue("transport", "rocket"))
                .thenReturn(Optional.empty());
        doNothing().when(entryValidator).validate(any());

        CarbonEntryRequest req = new CarbonEntryRequest();
        req.setCategory("transport");
        req.setType("rocket");
        req.setDistance(new BigDecimal("100"));
        req.setDistanceUnit("km");
        req.setPassengers(1);

        assertThatThrownBy(() -> carbonService.addEntry(1L, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown emission type");
    }

    @Test
    @DisplayName("Avoided emission types (solar) get AVOIDED_EMISSION calculation type")
    void avoidedEmissionType() {
        EmissionFactor factor = EmissionFactor.builder()
                .id(5L).category("energy").type("solar")
                .factor(new BigDecimal("0.050000")).unit("kg/kWh")
                .version(1).active(true).build();

        when(emissionFactorRepository.findByCategoryAndTypeAndActiveTrue("energy", "solar"))
                .thenReturn(Optional.of(factor));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(timezoneService.now()).thenReturn(java.time.Instant.now());
        when(carbonEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CarbonEntryRequest req = new CarbonEntryRequest();
        req.setCategory("energy");
        req.setType("solar");
        req.setConsumption(new BigDecimal("10"));
        req.setEnergyUnit("kWh");

        doNothing().when(entryValidator).validate(any());

        var response = carbonService.addEntry(1L, req);

        // Solar: AVOIDED_EMISSION
        assertThat(response.getCalculationType()).isEqualTo("AVOIDED_EMISSION");
        // CO2 value is POSITIVE (0.05 × 10 = 0.5), direction determined by type
        assertThat(response.getCo2()).isEqualByComparingTo("0.5000");
    }

    @Test
    @DisplayName("Secondhand modifier is stored and applied correctly")
    void secondhandModifier() {
        EmissionFactor factor = EmissionFactor.builder()
                .id(10L).category("shopping").type("clothing-kg")
                .factor(new BigDecimal("15.000000")).unit("kg CO2/kg")
                .version(1).active(true).build();

        when(emissionFactorRepository.findByCategoryAndTypeAndActiveTrue("shopping", "clothing-kg"))
                .thenReturn(Optional.of(factor));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(timezoneService.now()).thenReturn(java.time.Instant.now());
        when(carbonEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CarbonEntryRequest req = new CarbonEntryRequest();
        req.setCategory("shopping");
        req.setType("clothing-kg");
        req.setQuantity(new BigDecimal("2"));
        req.setQuantityUnit("kg");
        req.setIsSecondhand(true);

        doNothing().when(entryValidator).validate(any());

        var response = carbonService.addEntry(1L, req);

        // 15 × 2 × 0.5 = 15.0 kg (secondhand modifier)
        assertThat(response.getCo2()).isEqualByComparingTo("15.0000");
        assertThat(response.getModifierType()).isEqualTo("secondhand");
        assertThat(response.getModifierValue()).isEqualByComparingTo("0.5");
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }
}
