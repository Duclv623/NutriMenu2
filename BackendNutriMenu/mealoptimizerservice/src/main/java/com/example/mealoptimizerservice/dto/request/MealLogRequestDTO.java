package com.example.mealoptimizerservice.dto.request;

import jakarta.validation.constraints.*;

public record MealLogRequestDTO(@NotBlank String key, boolean completed) {}
