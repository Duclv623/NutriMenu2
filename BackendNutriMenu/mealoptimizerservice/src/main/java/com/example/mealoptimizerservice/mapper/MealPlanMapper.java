package com.example.mealoptimizerservice.mapper;

import com.example.mealoptimizerservice.client.MealAccountClient.*;
import com.example.mealoptimizerservice.dto.response.*;
import com.example.mealoptimizerservice.dto.response.PersonalMealPlanResponseDTO.*;
import com.example.mealoptimizerservice.entity.enums.MealItemStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.*;
import java.util.*;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class MealPlanMapper {
    private final ObjectMapper json;

    public PersonalMealPlanResponseDTO toResponse(MealAccount account, MealPlanResponse raw) {
        if (raw.getUserId() != account.serviceUserId()) throw new IllegalStateException("Meal plan owner mismatch");
        List<Item> items = new ArrayList<>();
        List<Item> skipped = new ArrayList<>();
        raw.getMealsBySlot().forEach((slot, rows) -> {
            String meal = switch (slot) {
                case BREAKFAST -> "breakfast";
                case LUNCH -> "lunch";
                case DINNER -> "dinner";
                case MORNING_SNACK, AFTERNOON_SNACK -> "snack";
            };
            for (var row : rows) {
                var destination = row.getStatus() == MealItemStatus.SKIPPED ? skipped : items;
                var nutrition = new Nutrition(row.getCaloriesKcal(), row.getNetCarbG(), row.getProteinG(),
                        row.getFatG(), row.getFiberG(), row.getSodiumMg());
                destination.add(new Item(row.getDishNameVi(), row.getItemId().toString(), row.getDishId().toString(), meal,
                        row.getServingMultiplier(), Boolean.TRUE.equals(row.getIsLocked()),
                        row.getStatus() == MealItemStatus.COMPLETED, nutrition));
            }
        });
        String id = raw.getPlanId().toString();
        PlanMetadata legacy = account.previousPlans().get(id);
        long profileVersion = raw.getSourceProfileVersion() != null ? raw.getSourceProfileVersion()
                : legacy == null ? 0 : legacy.profileVersion();
        String created = legacy != null ? legacy.createdAt() : raw.getCreatedAt() == null ? ""
                : raw.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toString();
        return new PersonalMealPlanResponseDTO(id, raw.getPlanDate().toString(), profileVersion, raw.getVersion() + 1,
                items, raw.getSolverStatus().name(), "api", raw.getTargetCaloriesKcal(), raw.getMedicalWarnings(),
                skipped, raw.getUserRating(), raw.getUserFeedback(), created, raw.isValidationPassed(), Objects.toString(raw.getChangeSignature(), ""));
    }

    private ObjectNode object(Object... values) {
        ObjectNode result = json.createObjectNode();
        for (int i = 0; i < values.length; i += 2) result.set((String) values[i], json.valueToTree(values[i + 1]));
        return result;
    }

    public ObjectNode snapshot(Profile profile, PersonalMealPlanResponseDTO plan, Map<String, JsonNode> dishes) {
        ArrayNode items = json.createArrayNode();
        ObjectNode totals = object("energyKcal", 0, "carbohydrateG", 0, "proteinG", 0, "fatG", 0, "fiberG", 0, "sodiumMg", 0);
        for (Item item : plan.items()) {
            JsonNode dish = dishes.get(item.foodId());
            ArrayNode ingredients = json.createArrayNode();
            dish.path("recipeBreakdown").forEach(i -> ingredients.add(object("id", i.path("ingredientId").asText(), "name", i.path("ingredientNameVi"))));
            Nutrition n = item.nutrition();
            ObjectNode nutrients = object("energyKcal", n.energy(), "carbohydrateG", n.carbs(), "proteinG", n.protein(),
                    "fatG", n.fat(), "fiberG", n.fiber(), "sodiumMg", n.sodium());
            nutrients.properties().forEach(entry -> totals.put(entry.getKey(),
                    totals.path(entry.getKey()).asDouble() + entry.getValue().asDouble()));
            String meal = item.meal().toUpperCase(java.util.Locale.ROOT);
            items.add(object("id", item.id(), "recipeId", item.foodId(), "recipeVersion", "unversioned", "name", dish.path("nameVi"),
                    "mealType", meal, "portionId", item.id(), "amount", item.servings(), "unit", "serving", "state", "unknown",
                    "nutritionBasis", "raw_ingredients_estimate", "nutrients", nutrients, "ingredients", ingredients, "ingredientsComplete", !ingredients.isEmpty(),
                    "sourceRefs", List.of("Food Catalog: " + dish.path("code").asText(), "Meal Optimizer: " + plan.id())));
        }
        return object("dataMode", "http", "profile", object("profileVersion", profile.version() + 1,
                "age", profile.age(), "gender", profile.gender(), "height", profile.height(), "weight", profile.weight(),
                "activityLevel", profile.activity(), "goal", profile.goal(), "allergies", profile.allergies(),
                "restrictedIngredients", profile.excludedFoods(), "scopeStatus", "unknown", "restrictionsResolved", false),
                "plan", object("id", plan.id(), "version", plan.version(), "profileVersion", plan.profileVersion() + 1,
                        "catalogVersion", "java-catalog-unversioned", "date", plan.date(), "solverStatus", plan.solverStatus(), "validationPassed", false,
                        "limitations", List.of("Danh mục chưa có phiên bản công thức; chưa xác nhận đầy đủ ràng buộc cá nhân."),
                        "target", object("version", Long.toString(plan.version()), "sourceRefs", List.of("Meal Optimizer targetCaloriesKcal"),
                                "bounds", object(), "pointTargets", object("energy_kcal", plan.target())), "items", items, "totals", totals));
    }
}
