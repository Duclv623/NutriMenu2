package com.example.mealoptimizerservice.solver;

import com.example.mealoptimizerservice.dto.DishSolverDto;
import com.example.mealoptimizerservice.entity.MealSlotTemplate;
import com.example.mealoptimizerservice.entity.SolverWeightProfile;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.service.GeneralReferencePolicy;
import com.google.ortools.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Historical full-nutrition40 fixture math regression; current runtime whitelist is tested separately with BULK. */
class NinSourceDatasetTest {
    @BeforeAll static void nativeSolver() { Loader.loadNativeLibraries(); }

    static List<DishSolverDto> actualSource() throws Exception {
        Path fixture = Path.of("../foodcatalogservice/src/main/resources/catalog/nin-real-20261123/nin-source-import.json");
        if (!Files.isRegularFile(fixture)) fixture = Path.of("foodcatalogservice/src/main/resources/catalog/nin-real-20261123/nin-source-import.json");
        var source = new ObjectMapper().readTree(Files.readString(fixture));
        var groups = new HashMap<String, IngredientGroup>();
        var ids = new HashMap<String, Long>();
        long nextId = 10000;
        for (var row : source.path("ingredients")) {
            groups.put(row.path("code").asText(), IngredientGroup.valueOf(row.path("group").asText()));
            ids.put(row.path("code").asText(), nextId++);
        }
        var candidates = new ArrayList<DishSolverDto>();
        for (var row : source.path("dishes")) {
            assertTrue(row.path("solverEligible").asBoolean(), row.path("code").asText());
            var n = row.path("nutrition");
            var slots = new HashSet<MealSlot>();
            for (var slot : row.path("mealSlots")) slots.add(MealSlot.valueOf(slot.asText()));
            var ingredients = new LinkedHashMap<Long, DishSolverDto.RawIngredientNeedDto>();
            IngredientGroup primary = IngredientGroup.MIXED;
            for (var item : row.path("recipeItems")) {
                String code = item.path("ingredientCode").asText();
                Long id = ids.get(code);
                ingredients.put(id, DishSolverDto.RawIngredientNeedDto.builder().ingredientId(id)
                        .ingredientNameVi(code).group(groups.get(code)).rawWeightG(item.path("rawWeightG").decimalValue()).build());
                if (item.path("mainIngredient").asBoolean()) primary = groups.get(code);
            }
            candidates.add(DishSolverDto.builder().dishId(1000L + candidates.size()).code(row.path("code").asText())
                    .nameVi(row.path("nameVi").asText()).role(DishRole.valueOf(row.path("dishRole").asText()))
                    .ingredientGroup(primary).validMealSlots(slots)
                    .caloriesX10(scale(n.path("caloriesKcal").decimalValue())).netCarbX10(scale(n.path("netCarbG").decimalValue()))
                    .fiberX10(scale(n.path("fiberG").decimalValue())).proteinX10(scale(n.path("proteinG").decimalValue()))
                    .fatX10(scale(n.path("fatG").decimalValue())).glX10(scale(n.path("glycemicLoad").decimalValue()))
                    .sodiumX10(scale(n.path("sodiumMg").decimalValue())).cholesterolX10(scale(n.path("cholesterolMg").decimalValue()))
                    .standardServingCookedG(row.path("sourceRawPortionWeightG").isNull() ? null : row.path("sourceRawPortionWeightG").decimalValue())
                    .minServingX2(row.path("minServingScaledX2").asInt()).maxServingX2(row.path("maxServingScaledX2").asInt())
                    .stepServingX2(row.path("stepServingScaledX2").asInt()).householdUnitLabel(row.path("householdUnitLabel").asText())
                    .rawIngredientsPerServing(ingredients).build());
        }
        assertEquals(40, candidates.size());
        SolverInputDataValidator.validate(candidates);
        return candidates;
    }

    private static long scale(BigDecimal v) { return v.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).longValueExact(); }

    static CpSatMealOptimizationEngine.SolverInputContext context(List<DishSolverDto> candidates) {
        return CpSatMealOptimizationEngine.SolverInputContext.builder().planningMode(PlanningMode.GENERAL_REFERENCE)
                .targetCaloriesKcal(new BigDecimal("1600")).targetNetCarbG(new BigDecimal("220"))
                .targetProteinG(new BigDecimal("70")).targetFatG(new BigDecimal("49"))
                .maxDailyGl(new BigDecimal("80")).maxDailySodiumMg(new BigDecimal("2400"))
                .maxDailyCholesterolMg(new BigDecimal("300")).minDailyFiberG(new BigDecimal("20"))
                .activeSlots(GeneralReferencePolicy.slots()).candidates(candidates)
                .weightProfile(SolverWeightProfile.builder().maxTimeSeconds(5.0).numSearchWorkers(1).build())
                .enableRandomPerturbation(false).build();
    }

    @Test void actualFortySourceRowsGenerateGeneralReferenceMenu() throws Exception {
        var candidates = actualSource();
        for (int energy : List.of(1600, 1800, 2000)) {
        var ctx = context(candidates);
        ctx.setTargetCaloriesKcal(BigDecimal.valueOf(energy));
        ctx.setTargetNetCarbG(BigDecimal.valueOf(energy * 0.55 / 4));
        ctx.setTargetProteinG(BigDecimal.valueOf(energy * 0.175 / 4));
        ctx.setTargetFatG(BigDecimal.valueOf(energy * 0.275 / 9));
        var result = new CpSatMealOptimizationEngine().solveOptimalMealPlan(ctx);
        assertTrue(Set.of(SolverRunStatus.OPTIMAL, SolverRunStatus.FEASIBLE).contains(result.getStatus()), result.getStatus().name());
        var selected = result.getSelectedDishes();
        assertTrue(selected.size() >= 6 && selected.size() <= 8);
        assertEquals(selected.size(), selected.stream().map(i -> i.getDish().getCode()).distinct().count());
        assertTrue(selected.stream().allMatch(i -> i.getDish().getCode().startsWith("NIN_REAL_")
                && i.getServingScaledX2() >= i.getDish().getMinServingX2()
                && i.getServingScaledX2() <= i.getDish().getMaxServingX2()
                && (i.getServingScaledX2() - i.getDish().getMinServingX2()) % i.getDish().getStepServingX2() == 0));
        long calories = selected.stream().mapToLong(i -> i.getDish().getCaloriesX10() * i.getServingScaledX2()).sum();
        long sodium = selected.stream().mapToLong(i -> i.getDish().getSodiumX10() * i.getServingScaledX2()).sum();
        long cholesterol = selected.stream().mapToLong(i -> i.getDish().getCholesterolX10() * i.getServingScaledX2()).sum();
        assertTrue(calories >= energy * 18 && calories <= energy * 22);
        assertTrue(sodium <= 48000);
        assertTrue(cholesterol <= 6000);
        assertEquals(1, selected.stream().filter(i -> i.getMealSlot() == MealSlot.BREAKFAST && i.getDish().getRole() == DishRole.COMPOSITE_DISH).count());
        for (var slot : List.of(MealSlot.LUNCH, MealSlot.DINNER)) {
            long composites = selected.stream().filter(i -> i.getMealSlot() == slot && i.getDish().getRole() == DishRole.COMPOSITE_DISH).count();
            long starches = selected.stream().filter(i -> i.getMealSlot() == slot && i.getDish().getRole() == DishRole.MAIN_STARCH).count();
            assertEquals(1, composites + starches);
            if (starches == 1) {
                assertEquals(1, selected.stream().filter(i -> i.getMealSlot() == slot && i.getDish().getRole() == DishRole.PROTEIN_DISH).count());
                assertTrue(selected.stream().filter(i -> i.getMealSlot() == slot && i.getDish().getRole() == DishRole.SOUP).count()<=1);
            }
        }
        for (var slot : List.of(MealSlot.LUNCH, MealSlot.DINNER))
            assertEquals(1, selected.stream().filter(i -> i.getMealSlot() == slot && i.getDish().getRole() == DishRole.VEGGIE_DISH).count());
        for (var slot : ctx.getActiveSlots()) {
            long mealCalories = selected.stream().filter(i -> i.getMealSlot() == slot.getMealSlot())
                    .mapToLong(i -> i.getDish().getCaloriesX10() * i.getServingScaledX2()).sum();
            double share = energy * slot.getCalorieRatioPct().doubleValue() / 100;
            assertTrue(mealCalories >= share * 0.6 * 20 - 1 && mealCalories <= share * 1.4 * 20 + 1);
        }
        System.out.println("NIN_GENERAL_" + energy + ": " + result.getStatus() + ", kcal=" + calories / 20.0 + ", sodiumMg=" + sodium / 20.0 + ", cholesterolMg=" + cholesterol / 20.0);
        selected.forEach(i -> System.out.println(i.getMealSlot() + ": " + i.getDish().getCode() + " x " + i.getServingScaledX2() / 2.0));
        }
    }

    @Test void historicalSourcesSupportTraditionalGeneralMealWithOptionalSoup() throws Exception {
        var candidates = actualSource();
        var context = context(candidates);
        var starch = candidates.stream().filter(d -> d.getRole() == DishRole.MAIN_STARCH).findFirst().orElseThrow();
        context.setLockedDishIdsBySlot(Map.of(MealSlot.LUNCH, Set.of(starch.getDishId())));
        var result = new CpSatMealOptimizationEngine().solveOptimalMealPlan(context);
        assertTrue(Set.of(SolverRunStatus.OPTIMAL, SolverRunStatus.FEASIBLE).contains(result.getStatus()), result.getStatus().name());
        var lunch = result.getSelectedDishes().stream().filter(i -> i.getMealSlot() == MealSlot.LUNCH).toList();
        for (var role : List.of(DishRole.MAIN_STARCH, DishRole.PROTEIN_DISH, DishRole.VEGGIE_DISH))
            assertEquals(1, lunch.stream().filter(i -> i.getDish().getRole() == role).count());
        assertTrue(lunch.size()==3 || lunch.size()==4);
        assertTrue(lunch.stream().filter(i -> i.getDish().getRole()==DishRole.SOUP).count()<=1);
        assertTrue(result.getSelectedDishes().stream().allMatch(i -> i.getDish().getCode().startsWith("NIN_REAL_")));
    }

    @Test void actualSourceMedicalStructureRemainsInfeasibleWithoutRequiredStarchCoverage() throws Exception {
        var candidates = actualSource();
        assertEquals(1, candidates.stream().filter(d -> d.getRole() == DishRole.MAIN_STARCH).count());
        var context = context(candidates);
        context.setPlanningMode(PlanningMode.MEDICAL);
        context.setActiveSlots(List.of(
                MealSlotTemplate.builder().mealSlot(MealSlot.LUNCH).allowCompositeDish(false).reqMainStarch(1)
                        .reqProteinDish(1).reqVeggieDish(1).reqSoup(1).minNetCarbG(new BigDecimal("30"))
                        .maxNetCarbG(new BigDecimal("68")).maxGl(new BigDecimal("24")).build(),
                MealSlotTemplate.builder().mealSlot(MealSlot.DINNER).allowCompositeDish(false).reqMainStarch(1)
                        .reqProteinDish(1).reqVeggieDish(1).reqSoup(1).minNetCarbG(new BigDecimal("30"))
                        .maxNetCarbG(new BigDecimal("68")).maxGl(new BigDecimal("24")).build()));
        var result = new CpSatMealOptimizationEngine().solveOptimalMealPlan(context);
        assertEquals(SolverRunStatus.INFEASIBLE, result.getStatus(), "One source starch cannot fill two distinct clinical meal slots; GI50 additionally excludes it.");
    }
}
