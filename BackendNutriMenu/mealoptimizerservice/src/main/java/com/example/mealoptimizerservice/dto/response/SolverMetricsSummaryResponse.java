package com.example.mealoptimizerservice.dto.response;

import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolverMetricsSummaryResponse {
    private long totalRuns;
    private long optimalCount;
    private long feasibleCount;
    private long infeasibleCount;
    private double successRatePct;
    private double avgSolveTimeMs;
    private int activeSolverThreads;
    private int queuedSolverTasks;
}
