package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.client.FoodCatalogIntegrationClient;
import com.example.mealoptimizerservice.dto.request.GenerateDailyMealRequest;
import com.example.mealoptimizerservice.dto.request.MealEditPolicy;
import com.example.mealoptimizerservice.dto.request.SwapSingleDishRequest;
import com.example.mealoptimizerservice.entity.MealPlan;
import com.example.mealoptimizerservice.entity.MealPlanItem;
import com.example.mealoptimizerservice.entity.enums.MedicalTag;
import com.example.mealoptimizerservice.entity.enums.PlanningMode;
import com.example.mealoptimizerservice.entity.MealSlotTemplate;
import com.example.mealoptimizerservice.entity.enums.MealSlot;
import com.example.mealoptimizerservice.dao.repository.*;
import com.example.mealoptimizerservice.solver.CpSatMealOptimizationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MealExclusionPipelineTest {
    private static class StopAtCatalog extends RuntimeException {}

    @Test void detailedSwapKeepsRecordedAndCurrentExclusionsAndTheGeneralMode() {
        var plans = mock(MealPlanRepository.class);
        var catalog = mock(FoodCatalogIntegrationClient.class);
        var item = MealPlanItem.builder().id(5L).dishId(6L).dishCode("NIN_BULK_TEST").mealSlot(MealSlot.BREAKFAST).build();
        var plan = MealPlan.builder().id(7L).userId(12L).version(2L).changePolicyRecorded(true)
                .planningMode(PlanningMode.GENERAL_REFERENCE).templateCode(GeneralReferencePolicy.TEMPLATE)
                .excludedDishIds(Set.of(8L)).contraindications(Set.of(MedicalTag.ALLERGEN_EGG))
                .items(new java.util.ArrayList<>(List.of(item))).build();
        when(plans.findOwnedForUpdate(7L,12L)).thenReturn(java.util.Optional.of(plan));
        when(catalog.fetchCandidatesForSolver(any(),any(),any(),any(),any(),any())).thenThrow(new StopAtCatalog());
        var service = new MealOptimizerService(mock(SolverWeightProfileRepository.class),mock(MealSlotTemplateRepository.class),plans,
                mock(CuratedFallbackPlanRepository.class),mock(SolverAuditLogRepository.class),catalog,
                mock(CpSatMealOptimizationEngine.class),mock(DiabeticClinicalTargetCalculator.class),null,mock(ThreadPoolTaskExecutor.class));
        var input = SwapSingleDishRequest.builder().droppedMealPlanItemId(5L)
                .policy(new MealEditPolicy(2L,Set.of(9L),Set.of(MedicalTag.ALLERGEN_SOY))).build();
        assertThrows(StopAtCatalog.class,() -> service.swapSingleDishInPlan(12L,7L,input));
        verify(catalog).fetchCandidatesForSolver(eq(12L),any(),eq(Set.of(MedicalTag.ALLERGEN_EGG,MedicalTag.ALLERGEN_SOY)),
                eq(Set.of(8L,9L)),any(),eq(PlanningMode.GENERAL_REFERENCE));
        verify(plans,never()).saveAndFlush(any());
    }

    private void verifyExclusions(Set<Long> explicit, Set<Long> recent, Set<Long> expected) {
        MealSlotTemplateRepository templates = mock(MealSlotTemplateRepository.class);
        FoodCatalogIntegrationClient catalog = mock(FoodCatalogIntegrationClient.class);
        when(templates.findByTemplateCodeOrderByIdAsc("STANDARD_4_MEALS"))
                .thenReturn(List.of(MealSlotTemplate.builder().mealSlot(MealSlot.BREAKFAST).build()));
        when(catalog.fetchCandidatesForSolver(any(), any(), any(), any(), any(), any()))
                .thenThrow(new StopAtCatalog());
        MealOptimizerService service = new MealOptimizerService(
                mock(SolverWeightProfileRepository.class), templates, mock(MealPlanRepository.class),
                mock(CuratedFallbackPlanRepository.class), mock(SolverAuditLogRepository.class), catalog,
                mock(CpSatMealOptimizationEngine.class), mock(DiabeticClinicalTargetCalculator.class), null,
                mock(ThreadPoolTaskExecutor.class));
        GenerateDailyMealRequest request = GenerateDailyMealRequest.builder().excludedDishIds(explicit).build();
        assertThrows(StopAtCatalog.class, () -> ReflectionTestUtils.invokeMethod(service,
                "runOptimizationPipelineWithExtraExclusions", 12L, request, recent, false, "TEST"));
        verify(catalog).fetchCandidatesForSolver(eq(12L), any(), any(), eq(expected), eq(Set.of(MealSlot.BREAKFAST)),any());
    }

    @Test
    void explicitExclusionsAreMergedWithRecentDishesBeforeCatalogSelection() {
        verifyExclusions(Set.of(4L, 8L), Set.of(8L, 9L), Set.of(4L, 8L, 9L));
    }

    @Test
    void callersWithoutExplicitExclusionsKeepExistingBehavior() {
        verifyExclusions(Set.of(), Set.of(9L), Set.of(9L));
    }
    @Test void generalMayReusePreviousDaysButKeepsExplicitUserExclusions() {
        var plans=mock(MealPlanRepository.class);
        Set<Long> allForty=java.util.stream.LongStream.rangeClosed(1,40).boxed().collect(java.util.stream.Collectors.toSet());
        when(plans.findRecentDishIdsByUserId(any(),any(),any())).thenReturn(allForty);
        var catalog=mock(FoodCatalogIntegrationClient.class);
        when(catalog.fetchCandidatesForSolver(any(),any(),any(),any(),any(),any())).thenThrow(new StopAtCatalog());
        var service=new MealOptimizerService(mock(SolverWeightProfileRepository.class),mock(MealSlotTemplateRepository.class),plans,
                mock(CuratedFallbackPlanRepository.class),mock(SolverAuditLogRepository.class),catalog,
                mock(CpSatMealOptimizationEngine.class),mock(DiabeticClinicalTargetCalculator.class),null,mock(ThreadPoolTaskExecutor.class));
        var request=GenerateDailyMealRequest.builder().planDate(java.time.LocalDate.of(2026,11,25))
                .planningMode(com.example.mealoptimizerservice.entity.enums.PlanningMode.GENERAL_REFERENCE).excludedDishIds(Set.of(8L)).build();
        assertThrows(StopAtCatalog.class,()->service.generateOptimalDailyMealPlan(12L,request));
        verify(catalog).fetchCandidatesForSolver(eq(12L),any(),any(),eq(Set.of(8L)),any(),any());
        verify(plans,never()).findRecentDishIdsByUserId(any(),any(),any());
    }
}
