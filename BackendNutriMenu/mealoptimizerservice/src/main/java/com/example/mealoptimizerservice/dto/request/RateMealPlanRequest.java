package com.example.mealoptimizerservice.dto.request;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateMealPlanRequest {
    @NotNull @Min(1) @Max(5)
    private Integer rating;
    private String feedback;
}
