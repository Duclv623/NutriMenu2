package com.example.mealoptimizerservice.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PersonalMealPlanResponseDTO(
        String id, String date, long profileVersion, long version, List<Item> items,
        String solverStatus, String dataMode, BigDecimal target, List<String> notes,
        List<Item> skippedItems, Integer rating, String feedback, String createdAt, boolean validationPassed, String changeSignature) {

    public record Item(String name, String id, String dishId, String meal, BigDecimal servingMultiplier,
                       boolean locked, boolean completed, Nutrition nutrition) {}

    public record Nutrition(BigDecimal energyKcal, BigDecimal netCarbG, BigDecimal proteinG,
                            BigDecimal fatG, BigDecimal fiberG, BigDecimal sodiumMg) {}
}
