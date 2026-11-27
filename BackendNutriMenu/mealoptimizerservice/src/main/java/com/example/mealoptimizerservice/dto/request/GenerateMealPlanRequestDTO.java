package com.example.mealoptimizerservice.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import com.example.mealoptimizerservice.entity.enums.PlanningMode;

public record GenerateMealPlanRequestDTO(@NotNull LocalDate date,
        @DecimalMin("1000") @DecimalMax("3500") double target,
        boolean snacks, @Min(0) int maxMinutes, boolean excludeRepeat, @Min(0) long profileVersion,
        @NotNull PlanningMode planningMode, @Positive Long replacePlanId,
        @Positive Long baseVersion, Boolean replacementConfirmed) {
    public GenerateMealPlanRequestDTO {
        replacementConfirmed = Boolean.TRUE.equals(replacementConfirmed);
    }
}
