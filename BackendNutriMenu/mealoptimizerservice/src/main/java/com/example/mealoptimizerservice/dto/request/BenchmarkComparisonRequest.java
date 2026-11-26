package com.example.mealoptimizerservice.dto.request;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BenchmarkComparisonRequest {
    @NotNull private Long profileIdA;
    @NotNull private Long profileIdB;
    @NotNull private GenerateDailyMealRequest sampleRequest;
}
