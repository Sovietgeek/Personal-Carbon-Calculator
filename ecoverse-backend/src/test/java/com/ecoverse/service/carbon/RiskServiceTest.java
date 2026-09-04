package com.ecoverse.service.carbon;

import com.ecoverse.dto.carbon.RiskAssessmentResponse;
import com.ecoverse.model.EmissionFactor;
import com.ecoverse.repository.EmissionFactorRepository;
import com.ecoverse.service.RiskService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Risk Service")
class RiskServiceTest {

    @Mock
    private EmissionFactorRepository emissionFactorRepository;

    @InjectMocks
    private RiskService riskService;

    // ===== Risk Assessment =====

    @Nested
    @DisplayName("Risk Assessment")
    class RiskAssessment {

        @Test
        @DisplayName("0% budget = EXCELLENT")
        void excellentLevel() {
            RiskAssessmentResponse response = riskService.assess(BigDecimal.ZERO, new BigDecimal("4.20"));
            assertThat(response.getLevel()).isEqualTo("EXCELLENT");
        }

        @Test
        @DisplayName("25% budget = GOOD")
        void goodLevel() {
            RiskAssessmentResponse response = riskService.assess(new BigDecimal("1.05"), new BigDecimal("4.20"));
            assertThat(response.getLevel()).isEqualTo("GOOD");
        }

        @Test
        @DisplayName("50% budget = MODERATE")
        void moderateLevel() {
            RiskAssessmentResponse response = riskService.assess(new BigDecimal("2.10"), new BigDecimal("4.20"));
            assertThat(response.getLevel()).isEqualTo("MODERATE");
        }

        @Test
        @DisplayName("80% budget = HIGH")
        void highLevel() {
            RiskAssessmentResponse response = riskService.assess(new BigDecimal("3.36"), new BigDecimal("4.20"));
            assertThat(response.getLevel()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("120% budget = EXTREME")
        void extremeLevel() {
            RiskAssessmentResponse response = riskService.assess(new BigDecimal("5.04"), new BigDecimal("4.20"));
            assertThat(response.getLevel()).isEqualTo("EXTREME");
        }

        @Test
        @DisplayName("Null emissions = EXCELLENT")
        void nullEmissions() {
            RiskAssessmentResponse response = riskService.assess(null, new BigDecimal("4.20"));
            assertThat(response.getLevel()).isEqualTo("EXCELLENT");
        }

        @Test
        @DisplayName("Zero budget = defaults to 4.2")
        void zeroBudget() {
            RiskAssessmentResponse response = riskService.assess(new BigDecimal("2.1"), BigDecimal.ZERO);
            assertThat(response.getLevel()).isNotNull();
        }
    }

    // ===== Risk Level (short version) =====

    @Nested
    @DisplayName("Risk Level String")
    class RiskLevelString {

        @Test
        @DisplayName("getRiskLevel returns correct string")
        void riskLevelString() {
            assertThat(riskService.getRiskLevel(BigDecimal.ZERO, new BigDecimal("4.20"))).isEqualTo("EXCELLENT");
            assertThat(riskService.getRiskLevel(new BigDecimal("1.0"), new BigDecimal("4.20"))).isEqualTo("GOOD");
            assertThat(riskService.getRiskLevel(new BigDecimal("2.5"), new BigDecimal("4.20"))).isEqualTo("MODERATE");
            assertThat(riskService.getRiskLevel(new BigDecimal("3.5"), new BigDecimal("4.20"))).isEqualTo("HIGH");
            assertThat(riskService.getRiskLevel(new BigDecimal("5.0"), new BigDecimal("4.20"))).isEqualTo("EXTREME");
        }
    }

    // ===== Trees Needed =====

    @Nested
    @DisplayName("Trees Needed")
    class TreesNeeded {

        @Test
        @DisplayName("0 emissions = 0 trees")
        void zeroEmissions() {
            assertThat(riskService.calculateTreesNeeded(BigDecimal.ZERO)).isEqualTo(0);
        }

        @Test
        @DisplayName("22 kg = 1 tree")
        void oneTree() {
            assertThat(riskService.calculateTreesNeeded(new BigDecimal("22"))).isEqualTo(1);
        }

        @Test
        @DisplayName("23 kg = 2 trees (rounded up)")
        void twoTrees() {
            assertThat(riskService.calculateTreesNeeded(new BigDecimal("23"))).isEqualTo(2);
        }

        @Test
        @DisplayName("880 kg = 40 trees")
        void fortyTrees() {
            assertThat(riskService.calculateTreesNeeded(new BigDecimal("880"))).isEqualTo(40);
        }

        @Test
        @DisplayName("Null emissions = 0 trees")
        void nullEmissions() {
            assertThat(riskService.calculateTreesNeeded(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("Negative emissions = 0 trees")
        void negativeEmissions() {
            assertThat(riskService.calculateTreesNeeded(new BigDecimal("-5"))).isEqualTo(0);
        }
    }

    // ===== Benchmarks from DB =====

    @Nested
    @DisplayName("Benchmarks from Database")
    class Benchmarks {

        @Test
        @DisplayName("India daily average from DB")
        void indiaAvgFromDb() {
            when(emissionFactorRepository.findByCategoryAndTypeAndActiveTrue("_benchmark", "india-daily-average"))
                    .thenReturn(Optional.of(EmissionFactor.builder().factor(new BigDecimal("4.20")).build()));
            assertThat(riskService.getIndiaDailyAverage()).isEqualByComparingTo("4.20");
        }

        @Test
        @DisplayName("Global daily average fallback when DB empty")
        void globalAvgFallback() {
            when(emissionFactorRepository.findByCategoryAndTypeAndActiveTrue("_benchmark", "global-daily-average"))
                    .thenReturn(Optional.empty());
            assertThat(riskService.getGlobalDailyAverage()).isEqualByComparingTo("8.5");
        }
    }
}
