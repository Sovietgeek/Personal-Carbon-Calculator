package com.ecoverse.service;

import com.ecoverse.dto.carbon.*;
import com.ecoverse.exception.BadRequestException;
import com.ecoverse.exception.ForbiddenException;
import com.ecoverse.exception.ResourceNotFoundException;
import com.ecoverse.model.CalculationType;
import com.ecoverse.model.CarbonEntry;
import com.ecoverse.model.EmissionFactor;
import com.ecoverse.model.User;
import com.ecoverse.repository.CarbonEntryRepository;
import com.ecoverse.repository.EmissionFactorRepository;
import com.ecoverse.repository.UserRepository;
import com.ecoverse.util.InputSanitizer;
import com.ecoverse.util.UnitConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Production Carbon Service — Phase D.
 *
 * Key changes from Phase C:
 * - Server calculates CO2 authoritatively (client NEVER sends CO2)
 * - All emission factor lookups from database (no hardcoded static map)
 * - BigDecimal for all carbon values
 * - Timezone-aware period calculations via TimezoneService
 * - CalculationType replaces negative CO2 convention
 * - DB aggregates replace in-memory loading for summary
 */
@Service
public class CarbonService {

    private static final Logger logger = LoggerFactory.getLogger(CarbonService.class);

    @Autowired
    private CarbonEntryRepository carbonEntryRepository;

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CarbonCalculationEngine calculationEngine;

    @Autowired
    private CarbonEntryValidator entryValidator;

    @Autowired
    private TimezoneService timezoneService;

    @Autowired
    private RiskService riskService;

    @Autowired
    private StreakService streakService;

    // ===== Calculation Preview =====

    /**
     * Preview/estimate calculation (POST /api/carbon/calculate).
     * Does NOT create an entry — returns calculated CO2 for user to review.
     */
    public EmissionCalcResponse calculateEmission(EmissionCalcRequest req) {
        String category = InputSanitizer.sanitize(req.getCategory(), 50);
        String type = InputSanitizer.sanitize(req.getType(), 50);

        EmissionFactor factor = lookupFactor(category, type);
        CalculationType calcType = calculationEngine.determineCalculationType(category, type);

        BigDecimal inputValue = req.getValue();
        String unit = req.getUnit() != null ? req.getUnit() : UnitConverter.getCanonicalUnit(category);

        // Convert input to canonical unit
        BigDecimal canonicalValue = convertToCanonical(category, inputValue, unit);

        // Calculate with modifiers
        Integer passengers = req.getPassengers() != null ? req.getPassengers() : 1;
        boolean isSecondhand = req.getIsSecondhand() != null && req.getIsSecondhand();
        String modifierType = isSecondhand ? "secondhand" : null;
        BigDecimal modifierValue = isSecondhand ? new BigDecimal("0.5") : null;

        BigDecimal co2 = calculationEngine.calculate(factor, canonicalValue, passengers, modifierType, modifierValue);

        return EmissionCalcResponse.builder()
                .co2(co2)
                .unit("kg CO2")
                .category(category)
                .type(type)
                .calculationType(calcType.name())
                .factorUsed(factor.getFactor())
                .factorVerificationStatus(factor.getVerificationStatus())
                .factorUncertainty(factor.getUncertainty())
                .build();
    }

    // ===== Entry CRUD =====

    /**
     * Add a new carbon entry. Server calculates CO2 authoritatively.
     * Client NEVER sends CO2 — it only sends input values (distance, consumption, etc.)
     * and the server looks up the emission factor and performs the calculation.
     */
    public CarbonEntryResponse addEntry(Long userId, CarbonEntryRequest req) {
        // 1. Validate the request
        entryValidator.validate(req);

        String category = InputSanitizer.sanitize(req.getCategory(), 50);
        String type = InputSanitizer.sanitize(req.getType(), 50);

        // 2. Look up emission factor from database
        EmissionFactor factor = lookupFactor(category, type);
        CalculationType calcType = calculationEngine.determineCalculationType(category, type);

        // 3. Resolve input value and unit
        BigDecimal inputValue = resolveInputValue(req);
        String inputUnit = resolveInputUnit(req);

        // 4. Convert to canonical unit
        BigDecimal canonicalValue = convertToCanonical(category, inputValue, inputUnit);

        // 5. Calculate CO2 authoritatively
        Integer passengers = resolvePassengers(req);
        boolean isSecondhand = req.getIsSecondhand() != null && req.getIsSecondhand();
        String modifierType = isSecondhand ? "secondhand" : null;
        BigDecimal modifierValue = isSecondhand ? new BigDecimal("0.5") : null;

        BigDecimal co2 = calculationEngine.calculate(factor, canonicalValue, passengers, modifierType, modifierValue);

        // 6. Get user timezone
        String userTimezone = resolveUserTimezone(userId, req.getTimezone());

        // 7. Build and save entry
        CarbonEntry entry = CarbonEntry.builder()
                .userId(userId)
                .category(category)
                .type(type)
                .co2(co2)
                .entryDate(timezoneService.now())
                .activityType(type)
                .inputValue(inputValue)
                .inputUnit(inputUnit)
                .factorId(factor.getId())
                .factorVersion(factor.getVersion())
                .calculationType(calcType.name())
                .modifierType(modifierType)
                .modifierValue(modifierValue)
                .userTimezone(userTimezone)
                .build();

        entry = carbonEntryRepository.save(entry);

        // Update best streak on mutation (NOT in GET endpoint)
        streakService.updateBestStreakIfNeeded(userId);

        logger.debug("Carbon entry created: userId={}, category={}, type={}, co2={} kg, calcType={}",
                userId, category, type, co2, calcType);

        return mapToResponse(entry);
    }

    public List<CarbonEntryResponse> getEntries(Long userId, String period) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);
        Instant[] range = timezoneService.getPeriodRange(period, zoneId);
        List<CarbonEntry> entries = carbonEntryRepository.findByUserIdAndEntryDateBetween(userId, range[0], range[1]);
        return entries.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    public void deleteEntry(Long entryId, Long userId) {
        CarbonEntry entry = carbonEntryRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("CarbonEntry", "id", entryId));

        if (!entry.getUserId().equals(userId)) {
            throw new ForbiddenException("You don't have access to this carbon entry");
        }

        carbonEntryRepository.deleteByIdAndUserId(entryId, userId);
    }

    public void clearTodayEntries(Long userId) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);
        Instant[] todayRange = timezoneService.getTodayRange(zoneId);
        List<CarbonEntry> todayEntries = carbonEntryRepository.findByUserIdAndEntryDateBetween(
                userId, todayRange[0], todayRange[1]);
        carbonEntryRepository.deleteAll(todayEntries);
    }

    // ===== Summary =====

    /**
     * Get carbon summary with DB aggregates (no in-memory loading of all entries).
     */
    public CarbonSummaryResponse getSummary(Long userId) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);

        // Period ranges
        Instant[] todayRange = timezoneService.getTodayRange(zoneId);
        Instant[] monthRange = timezoneService.getMonthRange(zoneId);
        Instant[] yearRange = timezoneService.getYearRange(zoneId);

        // Period emissions from DB aggregates
        BigDecimal todayEmissions = orZero(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(userId, todayRange[0], todayRange[1]));
        BigDecimal todayAvoided = orZero(carbonEntryRepository.sumAvoidedByUserIdAndPeriod(userId, todayRange[0], todayRange[1]));
        BigDecimal monthEmissions = orZero(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(userId, monthRange[0], monthRange[1]));
        BigDecimal yearEmissions = orZero(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(userId, yearRange[0], yearRange[1]));

        // Lifetime totals from DB aggregates
        BigDecimal totalEmitted = orZero(carbonEntryRepository.sumTotalEmissionsByUserId(userId));
        BigDecimal totalSaved = orZero(carbonEntryRepository.sumTotalAvoidedByUserId(userId));
        BigDecimal netImpact = totalEmitted.subtract(totalSaved);

        // Budget
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        BigDecimal budget = user.getCarbonBudget() != null ? user.getCarbonBudget() : new BigDecimal("4.20");
        BigDecimal budgetUsedPercent = budget.compareTo(BigDecimal.ZERO) > 0
                ? todayEmissions.multiply(new BigDecimal("100")).divide(budget, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal budgetRemaining = budget.subtract(todayEmissions).max(BigDecimal.ZERO);

        // Trees
        int treesNeeded = riskService.calculateTreesNeeded(yearEmissions);

        return CarbonSummaryResponse.builder()
                .todayEmissions(todayEmissions.setScale(2, RoundingMode.HALF_UP))
                .todayAvoided(todayAvoided.setScale(2, RoundingMode.HALF_UP))
                .monthEmissions(monthEmissions.setScale(2, RoundingMode.HALF_UP))
                .yearEmissions(yearEmissions.setScale(2, RoundingMode.HALF_UP))
                .totalEmitted(totalEmitted.setScale(2, RoundingMode.HALF_UP))
                .totalSaved(totalSaved.setScale(2, RoundingMode.HALF_UP))
                .netImpact(netImpact.setScale(2, RoundingMode.HALF_UP))
                .treesNeeded(treesNeeded)
                .budgetRemaining(budgetRemaining.setScale(2, RoundingMode.HALF_UP))
                .budgetUsedPercent(budgetUsedPercent.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    // ===== Risk Assessment =====

    public RiskAssessmentResponse getRiskAssessment(Long userId) {
        String userTimezone = getUserTimezone(userId);
        ZoneId zoneId = timezoneService.getUserZoneId(userTimezone);
        Instant[] todayRange = timezoneService.getTodayRange(zoneId);

        BigDecimal todayEmissions = orZero(carbonEntryRepository.sumEmissionsByUserIdAndPeriod(
                userId, todayRange[0], todayRange[1]));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        BigDecimal budget = user.getCarbonBudget() != null ? user.getCarbonBudget() : new BigDecimal("4.20");

        return riskService.assess(todayEmissions, budget);
    }

    // ===== Category Breakdown =====

    public Map<String, BigDecimal> getCategoryBreakdown(Long userId) {
        List<Object[]> breakdown = carbonEntryRepository.categoryEmissionBreakdownByUserId(userId);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : breakdown) {
            String category = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            result.put(category, total != null ? total.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        }
        return result;
    }

    // ===== Suggestions =====

    public List<Map<String, Object>> getSuggestions(Long userId) {
        Map<String, BigDecimal> breakdown = getCategoryBreakdown(userId);
        String highestCategory = null;
        BigDecimal highestEmission = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : breakdown.entrySet()) {
            if (entry.getValue().compareTo(highestEmission) > 0) {
                highestEmission = entry.getValue();
                highestCategory = entry.getKey();
            }
        }
        if (highestCategory == null) highestCategory = "general";

        List<Map<String, Object>> suggestions = new ArrayList<>();
        switch (highestCategory) {
            case "transport":
                suggestions.add(createSuggestion("Use Public Transport", "Switch to bus or train to reduce emissions by up to 80%", "transport", "high"));
                suggestions.add(createSuggestion("Carpooling", "Share rides to split emissions among passengers", "transport", "medium"));
                suggestions.add(createSuggestion("Electric Vehicle", "EVs produce 75% less emissions than petrol cars", "transport", "high"));
                suggestions.add(createSuggestion("Active Transport", "Walk or cycle for short distances under 5km", "transport", "medium"));
                break;
            case "energy":
                suggestions.add(createSuggestion("Solar Power", "Install solar panels to reduce grid electricity dependence", "energy", "high"));
                suggestions.add(createSuggestion("LED Lighting", "Switch to LED bulbs to reduce consumption by 75%", "energy", "medium"));
                suggestions.add(createSuggestion("Smart Thermostat", "Save up to 15% energy with smart temperature control", "energy", "medium"));
                break;
            case "food":
                suggestions.add(createSuggestion("Plant-Based Meals", "Vegan meals reduce food emissions by up to 93% vs beef", "food", "high"));
                suggestions.add(createSuggestion("Local & Organic", "Buy locally sourced produce to cut food miles", "food", "medium"));
                suggestions.add(createSuggestion("Less Red Meat", "Replacing beef with poultry reduces emissions by 72%", "food", "high"));
                break;
            case "shopping":
                suggestions.add(createSuggestion("Buy Secondhand", "Secondhand items reduce carbon footprint by 50%", "shopping", "high"));
                suggestions.add(createSuggestion("Sustainable Brands", "Choose brands with eco-friendly practices", "shopping", "medium"));
                break;
            case "waste":
                suggestions.add(createSuggestion("Recycle More", "Recycling saves 0.2 kg CO2 per kg vs landfill", "waste", "high"));
                suggestions.add(createSuggestion("Composting", "Compost organic waste to save 0.1 kg CO2 per kg", "waste", "medium"));
                suggestions.add(createSuggestion("E-Waste Recycling", "Properly recycle electronics — 4x more emissions in landfill", "waste", "high"));
                break;
            case "digital":
                suggestions.add(createSuggestion("Reduce Streaming Quality", "HD instead of 4K cuts streaming emissions by ~50%", "digital", "medium"));
                suggestions.add(createSuggestion("Limit Crypto", "A single crypto transaction = 25 kg CO2", "digital", "high"));
                break;
            default:
                suggestions.add(createSuggestion("Start Tracking", "Log daily activities to understand your carbon footprint", "general", "high"));
                suggestions.add(createSuggestion("Small Steps Matter", "Even small changes make a big difference over time", "general", "medium"));
                break;
        }
        return suggestions;
    }

    // ===== Private Helpers =====

    private EmissionFactor lookupFactor(String category, String type) {
        return emissionFactorRepository.findByCategoryAndTypeAndActiveTrue(category, type)
                .orElseThrow(() -> new BadRequestException(
                        "Unknown emission type '" + type + "' for category '" + category + "'"));
    }

    private BigDecimal resolveInputValue(CarbonEntryRequest req) {
        String category = req.getCategory().toLowerCase();
        switch (category) {
            case "transport": return req.getDistance();
            case "energy": return req.getConsumption();
            case "food": return req.getMeals() != null ? req.getMeals() : BigDecimal.ONE;
            case "shopping": return req.getQuantity();
            case "waste": return req.getQuantity();
            case "digital": return req.getQuantity();
            default: return BigDecimal.ONE;
        }
    }

    private String resolveInputUnit(CarbonEntryRequest req) {
        String category = req.getCategory().toLowerCase();
        switch (category) {
            case "transport": return req.getDistanceUnit() != null ? req.getDistanceUnit() : "km";
            case "energy": return req.getEnergyUnit() != null ? req.getEnergyUnit() : "kWh";
            case "food": return "meal";
            case "shopping": return req.getQuantityUnit() != null ? req.getQuantityUnit() : "kg";
            case "waste": return req.getQuantityUnit() != null ? req.getQuantityUnit() : "kg";
            case "digital": return req.getQuantityUnit() != null ? req.getQuantityUnit() : "hr";
            default: return "unit";
        }
    }

    private BigDecimal convertToCanonical(String category, BigDecimal value, String unit) {
        if (value == null) return BigDecimal.ONE;
        switch (category.toLowerCase()) {
            case "transport": return UnitConverter.toKm(value, unit);
            case "energy": return UnitConverter.toKwh(value, unit);
            case "food": return value; // meals are counts, no conversion needed
            case "shopping":
                if ("item".equalsIgnoreCase(unit)) return value; // per-item factor
                return UnitConverter.toKg(value, unit);
            case "waste": return UnitConverter.toKg(value, unit);
            case "digital": return value; // digital units are direct (hr, GB, txn, etc.)
            default: return value;
        }
    }

    private Integer resolvePassengers(CarbonEntryRequest req) {
        return req.getPassengers() != null && req.getPassengers() >= 1 ? req.getPassengers() : 1;
    }

    private String resolveUserTimezone(Long userId, String requestTimezone) {
        if (requestTimezone != null && !requestTimezone.isBlank()) {
            try {
                ZoneId.of(requestTimezone);
                return requestTimezone;
            } catch (Exception e) {
                // Invalid timezone, fall through to user profile
            }
        }
        return getUserTimezone(userId);
    }

    private String getUserTimezone(Long userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getTimezone() != null && !user.getTimezone().isBlank()) {
                return user.getTimezone();
            }
        } catch (Exception e) {
            logger.debug("Could not fetch user timezone for userId={}, using default", userId);
        }
        return "Asia/Kolkata"; // Default
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Map<String, Object> createSuggestion(String title, String description, String category, String priority) {
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("title", title);
        suggestion.put("description", description);
        suggestion.put("category", category);
        suggestion.put("priority", priority);
        return suggestion;
    }

    private CarbonEntryResponse mapToResponse(CarbonEntry entry) {
        return CarbonEntryResponse.builder()
                .id(entry.getId())
                .category(entry.getCategory())
                .type(entry.getType())
                .co2(entry.getCo2())
                .calculationType(entry.getCalculationType())
                .inputValue(entry.getInputValue())
                .inputUnit(entry.getInputUnit())
                .factorId(entry.getFactorId())
                .factorVersion(entry.getFactorVersion())
                .modifierType(entry.getModifierType())
                .modifierValue(entry.getModifierValue())
                .entryDate(entry.getEntryDate() != null ? entry.getEntryDate().toString() : null)
                .build();
    }
}
