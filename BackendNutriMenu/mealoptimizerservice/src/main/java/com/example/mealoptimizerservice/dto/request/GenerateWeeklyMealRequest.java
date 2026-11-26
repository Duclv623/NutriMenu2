package com.example.mealoptimizerservice.dto.request;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateWeeklyMealRequest {
    @NotNull
    private LocalDate startDate;
    @NotNull
    private GenerateDailyMealRequest dailyTargetTemplate;
}
