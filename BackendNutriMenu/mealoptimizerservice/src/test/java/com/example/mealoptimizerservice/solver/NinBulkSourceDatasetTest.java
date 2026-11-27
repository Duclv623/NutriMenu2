package com.example.mealoptimizerservice.solver;

import com.example.mealoptimizerservice.dto.DishSolverDto;
import com.example.mealoptimizerservice.dto.response.MealPlanResponse;
import com.example.mealoptimizerservice.entity.*;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.service.GeneralReferencePolicy;
import com.google.ortools.Loader;
import org.junit.jupiter.api.*;
import tools.jackson.databind.*;
import java.math.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real bulk source values and explicit egg/soy evidence, native CP-SAT; no DB or HTTP claims. */
class NinBulkSourceDatasetTest {
    @BeforeAll static void nativeLibraries() { Loader.loadNativeLibraries(); }
    static Long scaled(JsonNode value) { return value==null || value.isNull() || value.isMissingNode() ? null : value.decimalValue().multiply(BigDecimal.TEN).setScale(0,RoundingMode.HALF_UP).longValueExact(); }
    static List<DishSolverDto> sourceCandidates(boolean requireEggSoy) throws Exception {
        Path path=Path.of("../foodcatalogservice/src/main/resources/catalog/nin-bulk-20261126/nin-source-import.json");
        if(!Files.isRegularFile(path))path=Path.of("foodcatalogservice/src/main/resources/catalog/nin-bulk-20261126/nin-source-import.json");
        var mapper=new ObjectMapper(); var source=mapper.readTree(Files.readString(path));
        Map<String,IngredientGroup> groups=new HashMap<>(); Map<String,Long> identities=new HashMap<>(); long next=10000;
        for(var row:source.path("ingredients")) { String code=row.path("code").asString(); identities.put(code,next++);groups.put(code,IngredientGroup.valueOf(row.path("group").asString())); }
        List<DishSolverDto> candidates=new ArrayList<>();
        for(var row:source.path("dishes")) {
            if(!row.path("solverEligible").asBoolean())continue;
            var quality=mapper.readTree(row.path("qualityJson").asString());
            if(requireEggSoy && (!"ABSENT_CONFIRMED".equals(quality.path("perAllergenEvidence").path("ALLERGEN_EGG").path("status").asString())
                    || !"ABSENT_CONFIRMED".equals(quality.path("perAllergenEvidence").path("ALLERGEN_SOY").path("status").asString())))continue;
            var n=row.path("nutrition"); Set<MealSlot> slots=new HashSet<>();for(var slot:row.path("mealSlots"))slots.add(MealSlot.valueOf(slot.asString()));
            Map<Long,DishSolverDto.RawIngredientNeedDto> ingredients=new LinkedHashMap<>(); IngredientGroup primary=IngredientGroup.MIXED;
            for(var item:row.path("recipeItems")) {
                String code=item.path("ingredientCode").asString(); long id=identities.get(code);
                ingredients.put(id,DishSolverDto.RawIngredientNeedDto.builder().ingredientId(id).ingredientNameVi(code).group(groups.get(code)).rawWeightG(item.path("rawWeightG").decimalValue()).build());
                if(item.path("mainIngredient").asBoolean())primary=groups.get(code);
            }
            candidates.add(DishSolverDto.builder().dishId(1000L+candidates.size()).code(row.path("code").asString()).nameVi(row.path("nameVi").asString())
                    .role(DishRole.valueOf(row.path("dishRole").asString())).ingredientGroup(primary).validMealSlots(slots)
                    .caloriesX10(scaled(n.path("caloriesKcal"))).netCarbX10(scaled(n.path("netCarbG"))).proteinX10(scaled(n.path("proteinG"))).fatX10(scaled(n.path("fatG"))).sodiumX10(scaled(n.path("sodiumMg")))
                    .fiberX10(scaled(n.path("fiberG"))).glX10(scaled(n.path("glycemicLoad"))).cholesterolX10(scaled(n.path("cholesterolMg")))
                    .minServingX2(row.path("minServingScaledX2").asInt()).maxServingX2(row.path("maxServingScaledX2").asInt()).stepServingX2(row.path("stepServingScaledX2").asInt())
                    .rawIngredientsPerServing(ingredients).householdUnitLabel(row.path("householdUnitLabel").asString()).build());
        }
        return candidates;
    }
    @Test void actualBulkEggSoyMenuKeepsUnknownOptionalNutrientsUnknown() throws Exception {
        var candidates=sourceCandidates(true);
        assertTrue(candidates.size()>20,"Need real reviewed egg/soy candidates");
        SolverInputDataValidator.validate(candidates,PlanningMode.GENERAL_REFERENCE);
        assertThrows(IllegalArgumentException.class,()->SolverInputDataValidator.validate(candidates,PlanningMode.MEDICAL));
        boolean fiber=candidates.stream().allMatch(d -> d.getFiberX10()!=null),chol=candidates.stream().allMatch(d -> d.getCholesterolX10()!=null);
        assertFalse(fiber); assertFalse(chol);
        for (int target:List.of(1600,1800,2000)) {
        var ctx=CpSatMealOptimizationEngine.SolverInputContext.builder().planningMode(PlanningMode.GENERAL_REFERENCE).evaluateFiber(fiber).evaluateCholesterol(chol)
                .targetCaloriesKcal(BigDecimal.valueOf(target)).targetNetCarbG(new BigDecimal("247.5")).targetProteinG(new BigDecimal("78.75")).targetFatG(new BigDecimal("55"))
                .maxDailySodiumMg(new BigDecimal("2400")).activeSlots(GeneralReferencePolicy.slots()).candidates(candidates)
                .weightProfile(SolverWeightProfile.builder().maxTimeSeconds(1.5).numSearchWorkers(2).build()).enableRandomPerturbation(false).build();
        long start=System.nanoTime(); var result=new CpSatMealOptimizationEngine().solveOptimalMealPlan(ctx);
        assertTrue(Set.of(SolverRunStatus.FEASIBLE,SolverRunStatus.OPTIMAL).contains(result.getStatus()),result.getStatus()+"; candidates="+candidates.size());
        assertTrue((System.nanoTime()-start)/1_000_000<5000,"Must fit deployed bulkhead");
        long energy=result.getSelectedDishes().stream().mapToLong(i -> i.getDish().getCaloriesX10()*i.getServingScaledX2()).sum();
        long sodium=result.getSelectedDishes().stream().mapToLong(i -> i.getDish().getSodiumX10()*i.getServingScaledX2()).sum();
        assertTrue(energy>=target*18 && energy<=target*22);assertTrue(sodium<=48000);
        assertTrue(result.getSelectedDishes().stream().allMatch(i -> i.getDish().getCode().startsWith("NIN_BULK_")));
        assertEquals(result.getSelectedDishes().size(),result.getSelectedDishes().stream().map(i -> i.getDish().getDishId()).distinct().count());
        System.out.println("BULK_EGG_SOY_"+target+" "+result.getStatus()+" candidates="+candidates.size()+" kcal="+energy/20.0+" sodiumMg="+sodium/20.0);
        for(var item:result.getSelectedDishes())System.out.println(item.getMealSlot()+" "+item.getDish().getCode()+" x"+item.getServingScaledX2()/2.0);
        }
        var plan=MealPlan.builder().planningMode(PlanningMode.GENERAL_REFERENCE).evaluateFiber(false).evaluateCholesterol(false).solverStatus(SolverRunStatus.FEASIBLE).build();
        var item=new MealPlanItem();item.setSnapshotCaloriesKcal(BigDecimal.ONE);item.setSnapshotNetCarbG(BigDecimal.ONE);item.setSnapshotProteinG(BigDecimal.ONE);item.setSnapshotFatG(BigDecimal.ONE);
        item.setSnapshotFiberG(null);item.setSnapshotGl(null);item.setSnapshotCholesterolMg(null);item.setMealSlot(MealSlot.BREAKFAST);plan.getItems().add(item);plan.recalculateTotalsFromItems();
        assertNull(plan.getActualFiberG());assertNull(plan.getActualTotalGl());assertNull(plan.getActualCholesterolMg());
        var response=MealPlanResponse.fromEntity(plan,null,List.of());
        assertFalse(response.isEvaluateFiber());assertFalse(response.isEvaluateCholesterol());assertTrue(response.getMedicalWarnings().stream().anyMatch(s -> s.contains("chưa đánh giá")));
    }
    @Test void bulkGeneralProductionPoolFitsProductionBudget() throws Exception {
        var all=sourceCandidates(false);
        assertTrue(all.size()>500);
        Map<DishRole,List<DishSolverDto>> roles=new EnumMap<>(DishRole.class);
        all.stream().sorted(Comparator.comparing(DishSolverDto::getGlX10,Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(DishSolverDto::getCode))
                .forEach(d -> roles.computeIfAbsent(d.getRole(),key -> new ArrayList<>()).add(d));
        var candidates=roles.values().stream().flatMap(list -> list.stream().limit(25)).toList();
        assertEquals(138,candidates.size());
        var ctx=CpSatMealOptimizationEngine.SolverInputContext.builder().planningMode(PlanningMode.GENERAL_REFERENCE).evaluateFiber(false).evaluateCholesterol(false)
                .targetCaloriesKcal(new BigDecimal("1800")).targetNetCarbG(new BigDecimal("247.5")).targetProteinG(new BigDecimal("78.75")).targetFatG(new BigDecimal("55"))
                .maxDailySodiumMg(new BigDecimal("2400")).activeSlots(GeneralReferencePolicy.slots()).candidates(candidates)
                .weightProfile(SolverWeightProfile.builder().maxTimeSeconds(1.5).numSearchWorkers(2).build()).enableRandomPerturbation(false).build();
        long start=System.nanoTime();var result=new CpSatMealOptimizationEngine().solveOptimalMealPlan(ctx);long millis=(System.nanoTime()-start)/1_000_000;
        assertTrue(Set.of(SolverRunStatus.FEASIBLE,SolverRunStatus.OPTIMAL).contains(result.getStatus()),result.getStatus().name());
        assertTrue(millis<5000,"Production source pool must fit deployed bulkhead");
        long energy=result.getSelectedDishes().stream().mapToLong(i -> i.getDish().getCaloriesX10()*i.getServingScaledX2()).sum();
        long sodium=result.getSelectedDishes().stream().mapToLong(i -> i.getDish().getSodiumX10()*i.getServingScaledX2()).sum();
        assertTrue(energy>=32400 && energy<=39600);assertTrue(sodium<=48000);
        System.out.println("BULK_NO_ALLERGY_1800 "+result.getStatus()+" candidates="+candidates.size()+" millis="+millis+" kcal="+energy/20.0+" sodiumMg="+sodium/20.0);
    }
}
