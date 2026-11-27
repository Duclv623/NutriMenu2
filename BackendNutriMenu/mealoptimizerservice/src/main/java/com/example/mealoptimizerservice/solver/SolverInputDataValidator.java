package com.example.mealoptimizerservice.solver;

import com.example.mealoptimizerservice.dto.DishSolverDto;
import java.util.Arrays;
import com.example.mealoptimizerservice.entity.enums.PlanningMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reject omitted nutritional inputs; JSON omissions must never become zero. */
public final class SolverInputDataValidator {
    private SolverInputDataValidator() {}
    public static void validate(List<DishSolverDto> candidates) {
        validate(candidates,PlanningMode.MEDICAL);
    }
    public static void validate(List<DishSolverDto> candidates, PlanningMode mode) {
        if (candidates == null) throw new IllegalArgumentException("Catalog candidates missing");
        Set<Long> ids = new HashSet<>();
        for (DishSolverDto d : candidates) {
            if (d == null || d.getDishId() == null || !ids.add(d.getDishId()) || d.getRole() == null
                    || d.getIngredientGroup() == null || d.getValidMealSlots() == null || d.getValidMealSlots().isEmpty())
                throw new IllegalArgumentException("Invalid catalog candidate identity/slots");
            Long[] required = mode==PlanningMode.GENERAL_REFERENCE
                    ? new Long[]{d.getCaloriesX10(),d.getNetCarbX10(),d.getProteinX10(),d.getFatX10(),d.getSodiumX10()}
                    : new Long[]{d.getCaloriesX10(),d.getNetCarbX10(),d.getFiberX10(),d.getProteinX10(),d.getFatX10(),d.getGlX10(),d.getSodiumX10(),d.getCholesterolX10()};
            if (Arrays.stream(required)
                    .anyMatch(value -> value == null || value < 0) || d.getCaloriesX10() == 0)
                throw new IllegalArgumentException("Catalog candidate has missing/invalid nutrition: " + d.getCode());
            if (d.getMinServingX2() <= 0 || d.getStepServingX2() <= 0 || d.getMaxServingX2() < d.getMinServingX2()
                    || (d.getMaxServingX2() - d.getMinServingX2()) % d.getStepServingX2() != 0)
                throw new IllegalArgumentException("Invalid catalog candidate serving rules: " + d.getCode());
            if (d.getRawIngredientsPerServing() != null && d.getRawIngredientsPerServing().values().stream()
                    .anyMatch(item -> item == null || item.getRawWeightG() == null || item.getRawWeightG().signum() <= 0))
                throw new IllegalArgumentException("Catalog candidate has unknown ingredient grams: " + d.getCode());
        }
    }
}
