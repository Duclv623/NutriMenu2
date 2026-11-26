package com.example.mealoptimizerservice.dto.response;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutritionalAnalyticsResponse {
    private int analyzedDays;
    private int completedItemsCount;
    private BigDecimal adherenceRatePct;
    private BigDecimal avgDailyCaloriesKcal;
    private BigDecimal avgDailyNetCarbG;
    private BigDecimal avgDailyFiberG;
    private BigDecimal avgDailyGl;
}
