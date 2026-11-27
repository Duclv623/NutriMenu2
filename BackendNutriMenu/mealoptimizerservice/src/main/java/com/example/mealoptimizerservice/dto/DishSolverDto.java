package com.example.mealoptimizerservice.dto;

import com.example.mealoptimizerservice.entity.MealPlan;
import com.example.mealoptimizerservice.entity.MealPlanItem;
import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

// --- DTO giao tiếp với Food Catalog Service ---
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DishSolverDto {
    private Long dishId;
    private String code;
    private String nameVi;
    private DishRole role;
    private IngredientGroup ingredientGroup;
    private Set<MealSlot> validMealSlots;
    private Long caloriesX10;
    private Long netCarbX10;
    private Long fiberX10;
    private Long proteinX10;
    private Long fatX10;
    private Long glX10;
    private Long sodiumX10;
    private Long cholesterolX10;
    private BigDecimal standardServingCookedG;
    private int minServingX2;
    private int maxServingX2;
    private int stepServingX2;
    private int basePreferenceScore;
    private String householdUnitLabel;
    private Map<Long, RawIngredientNeedDto> rawIngredientsPerServing;

    /** Keep primitive builder calls compatible while JSON omissions remain null. */
    public static class DishSolverDtoBuilder {
        public DishSolverDtoBuilder caloriesX10(long value) { this.caloriesX10 = value; return this; }
        public DishSolverDtoBuilder netCarbX10(long value) { this.netCarbX10 = value; return this; }
        public DishSolverDtoBuilder fiberX10(long value) { this.fiberX10 = value; return this; }
        public DishSolverDtoBuilder fiberX10(Long value) { this.fiberX10 = value; return this; }
        public DishSolverDtoBuilder proteinX10(long value) { this.proteinX10 = value; return this; }
        public DishSolverDtoBuilder fatX10(long value) { this.fatX10 = value; return this; }
        public DishSolverDtoBuilder glX10(long value) { this.glX10 = value; return this; }
        public DishSolverDtoBuilder glX10(Long value) { this.glX10 = value; return this; }
        public DishSolverDtoBuilder sodiumX10(long value) { this.sodiumX10 = value; return this; }
        public DishSolverDtoBuilder cholesterolX10(long value) { this.cholesterolX10 = value; return this; }
        public DishSolverDtoBuilder cholesterolX10(Long value) { this.cholesterolX10 = value; return this; }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RawIngredientNeedDto {
        private Long ingredientId;
        private String ingredientNameVi;
        private IngredientGroup group;
        private BigDecimal rawWeightG;
    }
}
