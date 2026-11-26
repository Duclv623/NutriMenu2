package com.example.mealoptimizerservice.dto.request;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ManualReplaceItemRequest {
    @NotNull
    private Long newDishId;
    @NotNull @Min(1) @Max(6)
    private Integer newServingScaledX2;
}
