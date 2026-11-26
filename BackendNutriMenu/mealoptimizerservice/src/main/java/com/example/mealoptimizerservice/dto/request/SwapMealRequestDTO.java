package com.example.mealoptimizerservice.dto.request;

import jakarta.validation.constraints.*;

public record SwapMealRequestDTO(@Min(1) long baseVersion, @Min(0) long profileVersion,
                         @NotBlank String itemId, @NotBlank String foodId, @DecimalMin("0.5") @DecimalMax("3") double servings,
                         @Size(max=100) String previewSignature) {}
