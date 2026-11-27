package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.dto.DishSolverDto;
import com.example.mealoptimizerservice.entity.MealPlan;
import com.example.mealoptimizerservice.entity.MealSlotTemplate;
import com.example.mealoptimizerservice.entity.enums.DishRole;
import com.example.mealoptimizerservice.entity.enums.MealSlot;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

/** Production selection policy; historical snapshots remain readable. */
public final class NewSourceCatalogPolicy {
    private NewSourceCatalogPolicy() {}

    public static boolean isNewSource(String code) {
        return code != null && code.startsWith("NIN_BULK_");
    }

    public static List<DishSolverDto> onlyNewSource(List<DishSolverDto> dishes) {
        return dishes.stream().filter(d -> isNewSource(d.getCode())).toList();
    }

    public static void requireNewSourcePlan(MealPlan plan) {
        if (plan.getItems().stream().anyMatch(i -> !isNewSource(i.getDishCode()))) {
            throw new OptimizerBusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Thực đơn dùng dữ liệu cũ chỉ được xem lại. Hãy chọn ngày khác hoặc xóa thực đơn cũ trước khi tạo thực đơn bằng dữ liệu mới.");
        }
    }

    public static void requireCoverage(List<MealSlotTemplate> slots, List<DishSolverDto> dishes) {
        List<String> missing = new ArrayList<>();
        for (MealSlotTemplate slot : slots) {
            String meal = switch (slot.getMealSlot()) {
                case BREAKFAST -> "bữa sáng";
                case LUNCH -> "bữa trưa";
                case DINNER -> "bữa tối";
                default -> "bữa phụ";
            };
            if (GeneralReferencePolicy.TEMPLATE.equals(slot.getTemplateCode()) && slot.getReqMainStarch() > 0) {
                boolean composite = has(dishes,slot.getMealSlot(),DishRole.COMPOSITE_DISH);
                boolean traditional = has(dishes,slot.getMealSlot(),DishRole.MAIN_STARCH)
                        && has(dishes,slot.getMealSlot(),DishRole.PROTEIN_DISH);
                if (!composite && !traditional) missing.add("món hoàn chỉnh hoặc bộ tinh bột và đạm cho " + meal);
                check(missing,dishes,slot.getMealSlot(),DishRole.VEGGIE_DISH,slot.getReqVeggieDish(),meal,"món rau");
                continue;
            }
            if (Boolean.TRUE.equals(slot.getAllowCompositeDish()))
                check(missing, dishes, slot.getMealSlot(), DishRole.COMPOSITE_DISH, 1, meal, "món ăn hoàn chỉnh");
            check(missing, dishes, slot.getMealSlot(), DishRole.MAIN_STARCH, slot.getReqMainStarch(), meal, "món tinh bột");
            check(missing, dishes, slot.getMealSlot(), DishRole.PROTEIN_DISH, slot.getReqProteinDish(), meal, "món đạm");
            check(missing, dishes, slot.getMealSlot(), DishRole.VEGGIE_DISH, slot.getReqVeggieDish(), meal, "món rau");
            check(missing, dishes, slot.getMealSlot(), DishRole.SOUP, slot.getReqSoup(), meal, "món canh");
            check(missing, dishes, slot.getMealSlot(), DishRole.SNACK, slot.getReqSnack(), meal, "món ăn phụ");
        }
        if (!missing.isEmpty()) throw new OptimizerBusinessException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Dữ liệu mới chưa đủ để tạo thực đơn: thiếu " + String.join(", ", missing)
                        + ". Không sử dụng dữ liệu cũ để bù. Cần bổ sung dữ liệu dinh dưỡng và khẩu phần từ nguồn mới.");
    }

    private static boolean has(List<DishSolverDto> dishes, MealSlot slot, DishRole role) {
        return dishes.stream().anyMatch(d -> isNewSource(d.getCode()) && d.getRole() == role
                && d.getValidMealSlots() != null && d.getValidMealSlots().contains(slot));
    }

    private static void check(List<String> missing, List<DishSolverDto> dishes, MealSlot slot,
            DishRole role, Integer required, String meal, String label) {
        if (required != null && required > 0 && dishes.stream().filter(d -> isNewSource(d.getCode())
                && d.getRole() == role && d.getValidMealSlots() != null && d.getValidMealSlots().contains(slot)).count() < required)
            missing.add(label + " cho " + meal);
    }
}
