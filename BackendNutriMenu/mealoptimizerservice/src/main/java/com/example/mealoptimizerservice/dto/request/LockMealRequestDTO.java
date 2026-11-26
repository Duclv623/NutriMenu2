package com.example.mealoptimizerservice.dto.request;

import jakarta.validation.constraints.*;

public record LockMealRequestDTO(@Min(1) long baseVersion, boolean locked) {}
