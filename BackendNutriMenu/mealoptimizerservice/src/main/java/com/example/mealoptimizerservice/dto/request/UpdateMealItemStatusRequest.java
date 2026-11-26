package com.example.mealoptimizerservice.dto.request;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMealItemStatusRequest {
    @NotNull
    private MealItemStatus status;
}
