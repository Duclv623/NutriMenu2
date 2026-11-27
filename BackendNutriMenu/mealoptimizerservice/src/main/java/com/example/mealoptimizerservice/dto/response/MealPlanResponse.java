package com.example.mealoptimizerservice.dto.response;

import com.example.mealoptimizerservice.entity.MealPlan;
import com.example.mealoptimizerservice.entity.MealPlanItem;
import com.example.mealoptimizerservice.entity.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import com.example.mealoptimizerservice.dto.MathematicalBreakdownDto;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MealPlanResponse {
    private PlanningMode planningMode;
    private boolean evaluateFiber;
    private boolean evaluateCholesterol;
    private Long planId;
    private Long userId;
    private Long version;
    private String changeSignature;
    private boolean validationPassed;
    private Long sourceProfileVersion;
    private java.time.LocalDateTime createdAt;
    private LocalDate planDate;
    private SolverRunStatus solverStatus;
    private Long solveTimeMs;
    private BigDecimal targetCaloriesKcal;
    private BigDecimal actualCaloriesKcal;
    private BigDecimal targetNetCarbG;
    private BigDecimal actualNetCarbG;
    private BigDecimal actualFiberG;
    private BigDecimal targetProteinG;
    private BigDecimal actualProteinG;
    private BigDecimal targetFatG;
    private BigDecimal actualFatG;
    private BigDecimal actualTotalGl;
    private BigDecimal actualSodiumMg;
    private BigDecimal actualCholesterolMg;
    private Integer userRating;
    private String userFeedback;
    private Map<MealSlot, List<MealPlanItemResponse>> mealsBySlot;
    private List<String> medicalWarnings;
    private MathematicalBreakdownDto mathBreakdown;

    public static MealPlanResponse fromEntity(MealPlan plan, MathematicalBreakdownDto math, List<String> warnings) {
        Map<MealSlot, List<MealPlanItemResponse>> grouped = new EnumMap<>(MealSlot.class);
        for (MealPlanItem item : plan.getItems()) {
            grouped.computeIfAbsent(item.getMealSlot(), k -> new ArrayList<>())
                    .add(MealPlanItemResponse.fromEntity(item));
        }
        List<String> notices = new ArrayList<>(warnings != null ? warnings : Collections.emptyList());
        if (plan.getPlanningMode() == PlanningMode.GENERAL_REFERENCE
                && !notices.contains(com.example.mealoptimizerservice.service.GeneralReferencePolicy.NOTICE))
            notices.add(com.example.mealoptimizerservice.service.GeneralReferencePolicy.NOTICE);
        if (plan.getPlanningMode()==PlanningMode.GENERAL_REFERENCE && (!plan.isEvaluateFiber() || !plan.isEvaluateCholesterol()))
            notices.add("Phổ thông: chưa đánh giá " + (!plan.isEvaluateFiber()?"chất xơ ":"") + (!plan.isEvaluateCholesterol()?"cholesterol ":"") + "vì danh mục ứng viên thiếu số liệu; GI/GL không được đánh giá ở chế độ này. Số liệu thiếu giữ chưa biết, không thay bằng 0.");
        return MealPlanResponse.builder()
                .evaluateFiber(plan.isEvaluateFiber()).evaluateCholesterol(plan.isEvaluateCholesterol())
                .planningMode(plan.getPlanningMode() == null ? PlanningMode.MEDICAL : plan.getPlanningMode())
                .planId(plan.getId())
                .userId(plan.getUserId())
                .version(plan.getVersion())
                .sourceProfileVersion(plan.getSourceProfileVersion())
                .createdAt(plan.getCreatedAt())
                .planDate(plan.getPlanDate())
                .solverStatus(plan.getSolverStatus())
                .solveTimeMs(plan.getSolveTimeMs())
                .targetCaloriesKcal(plan.getTargetCaloriesKcal())
                .actualCaloriesKcal(plan.getActualCaloriesKcal())
                .targetNetCarbG(plan.getTargetNetCarbG())
                .actualNetCarbG(plan.getActualNetCarbG())
                .actualFiberG(plan.getActualFiberG())
                .targetProteinG(plan.getTargetProteinG())
                .actualProteinG(plan.getActualProteinG())
                .targetFatG(plan.getTargetFatG())
                .actualFatG(plan.getActualFatG())
                .actualTotalGl(plan.getActualTotalGl())
                .actualSodiumMg(plan.getActualSodiumMg())
                .actualCholesterolMg(plan.getActualCholesterolMg())
                .userRating(plan.getUserRating())
                .userFeedback(plan.getUserFeedback())
                .mealsBySlot(grouped)
                .medicalWarnings(notices)
                .mathBreakdown(math)
                .build();
    }
}
