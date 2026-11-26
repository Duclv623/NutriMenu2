package com.example.mealoptimizerservice.dto.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record GenerateMealPlanRequestDTO(@NotNull LocalDate date, @Min(1000) @Max(3500) double target,
                           boolean snacks, @Min(0) int maxMinutes, boolean excludeRepeat, @Min(0) long profileVersion) {}
