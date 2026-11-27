package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.dto.request.GenerateDailyMealRequest;
import com.example.mealoptimizerservice.entity.MealSlotTemplate;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.List;

/** Versioned server-owned reference policy, separate from clinical templates. */
public final class GeneralReferencePolicy {
    public static final String TEMPLATE = "GENERAL_REFERENCE_4_MEALS_V2";
    public static final String NOTICE = "Phổ thông / tham khảo: dùng để thử thực đơn, chưa được phê duyệt cho điều trị bệnh lý. Có thể lặp món giữa các ngày. Mục tiêu năng lượng do người dùng chọn; chỉ số dinh dưỡng là ước tính từ công thức.";
    private GeneralReferencePolicy() {}
    public static boolean isGeneral(PlanningMode mode) { return mode == PlanningMode.GENERAL_REFERENCE; }
    public static void normalize(GenerateDailyMealRequest request) {
        if (request.getPlanningMode() == null) throw new OptimizerBusinessException(HttpStatus.BAD_REQUEST, "Cần chọn chế độ thực đơn hợp lệ.");
        if (isGeneral(request.getPlanningMode())) {
            request.setTemplateCode(TEMPLATE);
            request.setMaxGiAllowed(new BigDecimal("100"));
            request.setMaxDailyGl(new BigDecimal("80")); // Stored for compatibility; general solver does not apply a clinical GL ceiling.
            request.setMaxDailySodiumMg(new BigDecimal("2400"));
            request.setMaxDailyCholesterolMg(new BigDecimal("300"));
            request.setMinDailyFiberG(new BigDecimal("20"));
            BigDecimal energy = request.getTargetCaloriesKcal();
            request.setTargetNetCarbG(energy.multiply(new BigDecimal("0.55")).divide(new BigDecimal("4")));
            request.setTargetProteinG(energy.multiply(new BigDecimal("0.175")).divide(new BigDecimal("4")));
            request.setTargetFatG(energy.multiply(new BigDecimal("0.275")).divide(new BigDecimal("9"), 2, java.math.RoundingMode.HALF_UP));
        } else {
            if (TEMPLATE.equals(request.getTemplateCode())) throw new OptimizerBusinessException(HttpStatus.BAD_REQUEST, "Mẫu phổ thông cần chế độ phổ thông.");
            if (request.getMaxGiAllowed() == null || request.getMaxGiAllowed().compareTo(new BigDecimal("50")) > 0
                    || request.getMaxDailyGl() == null || request.getMaxDailyGl().compareTo(new BigDecimal("80")) > 0)
                throw new OptimizerBusinessException(HttpStatus.BAD_REQUEST, "Chế độ bệnh lý giữ giới hạn GI 50 và GL 80.");
        }
    }
    public static List<MealSlotTemplate> slots() {
        return List.of(slot(MealSlot.BREAKFAST,30,true,0,0), slot(MealSlot.LUNCH,30,true,1,0),
                slot(MealSlot.DINNER,30,true,1,0), slot(MealSlot.AFTERNOON_SNACK,10,false,0,1));
    }
    private static MealSlotTemplate slot(MealSlot slot,int ratio,boolean composite,int vegetables,int snacks) {
        return MealSlotTemplate.builder().templateCode(TEMPLATE).mealSlot(slot).calorieRatioPct(BigDecimal.valueOf(ratio))
                .minNetCarbG(BigDecimal.ZERO).maxNetCarbG(new BigDecimal("350")).maxGl(new BigDecimal("350"))
                .allowCompositeDish(composite).reqVeggieDish(vegetables).reqSnack(snacks)
                .reqMainStarch(composite ? 1 : 0).reqProteinDish(composite ? 1 : 0).reqSoup(0).build();
    }
}
