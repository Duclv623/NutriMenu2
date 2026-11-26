package com.example.mealoptimizerservice.dto.response;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfeasibilityDiagnosisResponse {
    private boolean isFeasibleUnderStandardRules;
    private List<String> detectedConflicts;
    private List<String> actionableRecommendations;
}
