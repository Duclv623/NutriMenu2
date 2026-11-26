package com.example.mealoptimizerservice.dto.response;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkComparisonResponse {
    private String profileNameA;
    private MealPlanResponse resultA;
    private String profileNameB;
    private MealPlanResponse resultB;
    private String comparisonConclusionVi;
}
