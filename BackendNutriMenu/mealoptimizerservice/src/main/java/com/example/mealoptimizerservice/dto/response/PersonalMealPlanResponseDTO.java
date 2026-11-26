package com.example.mealoptimizerservice.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record PersonalMealPlanResponseDTO(
        String id, String date, long profileVersion, long version, List<Item> items,
        String solverStatus, String dataMode, BigDecimal target, List<String> notes,
        List<Item> skippedItems, Integer rating, String feedback, String createdAt, boolean validationPassed, String changeSignature) {

    public record Item(String name, String id, String foodId, String meal, BigDecimal servings,
                       boolean locked, boolean completed, Nutrition nutrition) {}

    public record Nutrition(BigDecimal energy, BigDecimal carbs, BigDecimal protein,
                            BigDecimal fat, BigDecimal fiber, BigDecimal sodium) {}
}
