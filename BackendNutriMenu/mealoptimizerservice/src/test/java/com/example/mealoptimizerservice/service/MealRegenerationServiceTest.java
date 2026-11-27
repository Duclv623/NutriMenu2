package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.client.FoodCatalogIntegrationClient;
import com.example.mealoptimizerservice.controller.UserMealPlanController;
import com.example.mealoptimizerservice.dto.*;
import com.example.mealoptimizerservice.entity.*;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.exception.GlobalExceptionHandler;
import com.example.mealoptimizerservice.dao.repository.*;
import com.example.mealoptimizerservice.solver.CpSatMealOptimizationEngine;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real HTTP binding/service/archive serialization; controlled repositories/solver, no live DB claim. */
class MealRegenerationServiceTest {
    MealPlanRepository plans;
    MealPlanRevisionRepository revisions;
    CpSatMealOptimizationEngine engine;
    ThreadPoolTaskExecutor executor;
    MealPlan current;
    MockMvc mvc;
    final ObjectMapper mapper=new ObjectMapper();
    static final LocalDate DATE=LocalDate.of(2026,11,24);
    static final String JSON="""
        {"planningMode":"GENERAL_REFERENCE","planDate":"2026-11-24","targetCaloriesKcal":1800,
         "replacePlanId":7,"expectedVersion":4,"replacementConfirmed":true,"previousProfileVersion":1}
        """;

    @BeforeEach void setup() {
        plans=mock(MealPlanRepository.class); revisions=mock(MealPlanRevisionRepository.class);
        engine=mock(CpSatMealOptimizationEngine.class);
        var catalog=mock(FoodCatalogIntegrationClient.class);
        var templates=mock(MealSlotTemplateRepository.class);
        executor=new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1); executor.initialize();
        var source=List.of(dish(1,MealSlot.BREAKFAST,DishRole.COMPOSITE_DISH),dish(2,MealSlot.LUNCH,DishRole.COMPOSITE_DISH),
                dish(3,MealSlot.DINNER,DishRole.COMPOSITE_DISH),dish(4,MealSlot.LUNCH,DishRole.VEGGIE_DISH),
                dish(5,MealSlot.DINNER,DishRole.VEGGIE_DISH),dish(6,MealSlot.AFTERNOON_SNACK,DishRole.SNACK));
        when(catalog.fetchCandidatesForSolver(any(),any(),anySet(),anySet(),anySet(),any())).thenReturn(source);
        var selected=source.stream().map(d -> CpSatMealOptimizationEngine.SelectedDishSolution.builder()
                .dish(d).mealSlot(d.getValidMealSlots().iterator().next()).servingScaledX2(2).build()).toList();
        when(engine.solveOptimalMealPlan(any())).thenReturn(CpSatMealOptimizationEngine.SolverExecutionResult.builder()
                .status(SolverRunStatus.FEASIBLE).selectedDishes(selected).breakdown(MathematicalBreakdownDto.builder().solveTimeMs(1L).build()).build());
        var service=new MealOptimizerService(mock(SolverWeightProfileRepository.class),templates,plans,
                mock(CuratedFallbackPlanRepository.class),mock(SolverAuditLogRepository.class),catalog,engine,
                mock(DiabeticClinicalTargetCalculator.class),new MealPlanArchiveService(revisions,mapper),executor);
        var oldDish=dish(90,MealSlot.BREAKFAST,DishRole.COMPOSITE_DISH); oldDish.setCode("QA_OLD");
        var oldItem=new MealPlanItem(); oldItem.setId(9L); oldItem.setMealSlot(MealSlot.BREAKFAST);
        CheckedMealChangeService.writeItem(oldItem,oldDish,2);
        current=MealPlan.builder().id(7L).userId(99L).planDate(DATE).version(4L).planningMode(PlanningMode.MEDICAL)
                .targetCaloriesKcal(new BigDecimal("1600")).targetNetCarbG(new BigDecimal("220"))
                .targetProteinG(new BigDecimal("70")).targetFatG(new BigDecimal("49"))
                .solverStatus(SolverRunStatus.FEASIBLE).userRating(4).userFeedback("Before")
                .items(new ArrayList<>(List.of(oldItem))).build();
        oldItem.setMealPlan(current); current.recalculateTotalsFromItems();
        when(plans.findOwnedForUpdate(7L,99L)).thenReturn(Optional.of(current));
        when(plans.findByUserIdAndPlanDate(99L,DATE)).thenReturn(Optional.of(current));
        when(plans.saveAndFlush(any())).thenAnswer(call -> { MealPlan p=call.getArgument(0); p.setVersion(p.getVersion()+1); return p; });
        mvc=MockMvcBuilders.standaloneSetup(new UserMealPlanController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    @AfterEach void shutdown() { executor.shutdown(); }
    private DishSolverDto dish(long id,MealSlot slot,DishRole role) {
        return DishSolverDto.builder().dishId(id).code("NIN_BULK_TEST_"+id).nameVi("Controlled source "+id).role(role)
                .ingredientGroup(IngredientGroup.MIXED).validMealSlots(Set.of(slot)).minServingX2(1).maxServingX2(6).stepServingX2(1)
                .caloriesX10(4000L).netCarbX10(450L).proteinX10(200L).fatX10(100L).fiberX10(30L)
                .glX10(200L).sodiumX10(1000L).cholesterolX10(100L).householdUnitLabel("1 source portion").build();
    }
    private int postBody(String body,long user) throws Exception {
        return mvc.perform(post("/api/v1/users/me/meal-plans/generate-daily").header("X-User-Id",user)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
    }
    private void untouched() {
        assertEquals(4L,current.getVersion()); assertEquals("QA_OLD",current.getItems().getFirst().getDishCode());
        verify(revisions,never()).save(any()); verify(plans,never()).saveAndFlush(any()); verify(plans,never()).delete(any());
    }
    @Test void explicitReplacementArchivesLegacyBeforeImageAndKeepsIdWithNewVersion() throws Exception {
        assertEquals(201,postBody(JSON,99L));
        assertEquals(7L,current.getId()); assertEquals(5L,current.getVersion());
        assertEquals(PlanningMode.GENERAL_REFERENCE,current.getPlanningMode());
        assertTrue(current.getItems().stream().allMatch(i -> i.getDishCode().startsWith("NIN_BULK_")));
        var archive=org.mockito.ArgumentCaptor.forClass(MealPlanRevision.class); verify(revisions).save(archive.capture());
        var snapshot=mapper.readTree(archive.getValue().getSnapshotJson());
        assertEquals(4L,archive.getValue().getOriginalVersion());
        assertEquals("QA_OLD",snapshot.get("mealPlan").get("mealsBySlot").get("BREAKFAST").get(0).get("dishCode").asString());
        assertEquals("PLANNED",snapshot.get("mealPlan").get("mealsBySlot").get("BREAKFAST").get(0).get("status").asString());
        assertFalse(snapshot.get("mealPlan").get("mealsBySlot").get("BREAKFAST").get(0).get("isLocked").asBoolean());
        assertEquals("MEDICAL",snapshot.get("mealPlan").get("planningMode").asString());
        assertEquals(50,snapshot.get("policy").get("maxGiAllowed").asInt());
        assertEquals(1,snapshot.get("profileVersion").asInt());
        assertEquals("Before",snapshot.get("mealPlan").get("userFeedback").asString());
        var order=inOrder(engine,plans,revisions);
        order.verify(engine).solveOptimalMealPlan(any()); order.verify(plans).findOwnedForUpdate(7L,99L);
        order.verify(revisions).save(any()); order.verify(plans).saveAndFlush(current);
        verify(plans,never()).delete(any());
    }
    @Test void infeasibleResultDoesNotLoadOrReplaceExistingPlan() throws Exception {
        when(engine.solveOptimalMealPlan(any())).thenReturn(CpSatMealOptimizationEngine.SolverExecutionResult.builder()
                .status(SolverRunStatus.INFEASIBLE).selectedDishes(List.of()).breakdown(MathematicalBreakdownDto.builder().build()).build());
        assertEquals(422,postBody(JSON,99L)); untouched(); verify(plans,never()).findOwnedForUpdate(any(),any());
    }
    @Test void staleVersionDoesNotArchiveOrWrite() throws Exception {
        assertEquals(409,postBody(JSON.replace("\"expectedVersion\":4","\"expectedVersion\":3"),99L)); untouched();
    }
    @Test void anotherOwnersPlanCannotBeReplaced() throws Exception {
        assertEquals(404,postBody(JSON,100L)); untouched();
    }
    @Test void anotherDateCannotBeReplaced() throws Exception {
        assertEquals(409,postBody(JSON.replace("2026-11-24","2026-11-25"),99L)); untouched();
    }
    @Test void missingConfirmationCannotWrite() throws Exception {
        assertEquals(400,postBody(JSON.replace("\"replacementConfirmed\":true","\"replacementConfirmed\":false"),99L)); untouched();
        verifyNoInteractions(engine);
    }
    @Test void lockedItemMustBeUnlockedFirst() throws Exception {
        current.getItems().getFirst().setIsLocked(true);
        assertEquals(409,postBody(JSON,99L)); untouched(); assertTrue(current.getItems().getFirst().getIsLocked());
    }
    @Test void consumedItemMustNotBeErased() throws Exception {
        current.getItems().getFirst().setStatus(MealItemStatus.COMPLETED);
        assertEquals(409,postBody(JSON,99L)); untouched(); assertEquals(MealItemStatus.COMPLETED,current.getItems().getFirst().getStatus());
    }
    @Test void ordinaryCreationCannotOverwriteExistingDay() throws Exception {
        String body="{\"planningMode\":\"GENERAL_REFERENCE\",\"planDate\":\"2026-11-24\",\"targetCaloriesKcal\":1800}";
        assertEquals(409,postBody(body,99L)); untouched(); verifyNoInteractions(engine);
    }
    @Test void archiveFailurePreventsAnyReplacement() throws Exception {
        doThrow(new IllegalStateException("Archive unavailable")).when(revisions).save(any());
        assertThrows(jakarta.servlet.ServletException.class,()->postBody(JSON,99L));
        assertEquals("QA_OLD",current.getItems().getFirst().getDishCode()); assertEquals(4L,current.getVersion());
        verify(plans,never()).saveAndFlush(any());
    }
}
