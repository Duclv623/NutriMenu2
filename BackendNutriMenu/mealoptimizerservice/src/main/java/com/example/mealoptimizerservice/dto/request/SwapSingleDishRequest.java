package com.example.mealoptimizerservice.dto.request;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwapSingleDishRequest {
    @NotNull
    private Long droppedMealPlanItemId;
    @Builder.Default
    private Set<Long> additionalExcludedDishIds = new HashSet<>();
}
