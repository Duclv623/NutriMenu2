package com.example.mealoptimizerservice.solver;

import com.example.mealoptimizerservice.dto.DishSolverDto;
import com.example.mealoptimizerservice.entity.enums.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SolverInputDataValidatorTest {
    private DishSolverDto known() {
        return DishSolverDto.builder().dishId(1L).code("KNOWN").role(DishRole.SNACK).ingredientGroup(IngredientGroup.FRUIT)
                .validMealSlots(Set.of(MealSlot.AFTERNOON_SNACK)).caloriesX10(360).netCarbX10(82).fiberX10(14)
                .proteinX10(9).fatX10(1).glX10(35).sodiumX10(39).cholesterolX10(0)
                .minServingX2(2).maxServingX2(2).stepServingX2(2).build();
    }
    @Test void missingJsonNutritionRemainsUnknownRatherThanZero() throws Exception {
        DishSolverDto omitted = new ObjectMapper().readValue("{\"minServingX2\":2,\"maxServingX2\":2,\"stepServingX2\":2,\"basePreferenceScore\":0}", DishSolverDto.class);
        assertNull(omitted.getSodiumX10()); assertNull(omitted.getGlX10()); assertNull(omitted.getCholesterolX10());
    }
    @Test void missingUniversalConstraintMetricsAreRejectedBeforeNativeSolver() {
        DishSolverDto missingSodium = known(); missingSodium.setSodiumX10(null);
        assertThrows(IllegalArgumentException.class, () -> SolverInputDataValidator.validate(List.of(missingSodium)));
        DishSolverDto missingCholesterol = known(); missingCholesterol.setCholesterolX10(null);
        assertThrows(IllegalArgumentException.class, () -> SolverInputDataValidator.validate(List.of(missingCholesterol)));
        DishSolverDto missingGl = known(); missingGl.setGlX10(null);
        assertThrows(IllegalArgumentException.class, () -> new CpSatMealOptimizationEngine().solveOptimalMealPlan(
                CpSatMealOptimizationEngine.SolverInputContext.builder().candidates(List.of(missingGl)).build()));
    }
    @Test void documentedZeroIsAllowedButNegativeAndMissingValuesAreNot() {
        assertDoesNotThrow(() -> SolverInputDataValidator.validate(List.of(known())));
        DishSolverDto invalid = known(); invalid.setNetCarbX10(-1L);
        assertThrows(IllegalArgumentException.class, () -> SolverInputDataValidator.validate(List.of(invalid)));
    }
}
