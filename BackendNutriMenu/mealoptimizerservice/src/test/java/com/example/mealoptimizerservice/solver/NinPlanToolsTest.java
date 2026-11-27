package com.example.mealoptimizerservice.solver;

import com.example.mealoptimizerservice.client.FoodCatalogIntegrationClient;
import com.example.mealoptimizerservice.dao.repository.*;
import com.example.mealoptimizerservice.dto.request.*;
import com.example.mealoptimizerservice.entity.*;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.service.*;
import com.google.ortools.Loader;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NinPlanToolsTest {
    @Test void rebalanceGeneralPlanKeepsEatenSnapshotsAndUsesRemainingEnergyShares() throws Exception {
        Loader.loadNativeLibraries();
        var catalog = mock(FoodCatalogIntegrationClient.class);
        var candidates = NinBulkSourceDatasetTest.sourceCandidates(true);
        when(catalog.fetchCandidatesForSolver(any(),any(),anySet(),anySet(),anySet(),any())).thenAnswer(call -> {
            Set<Long> excluded = call.getArgument(3);
            return candidates.stream().filter(dish -> !excluded.contains(dish.getDishId())).toList();
        });
        var weights = mock(SolverWeightProfileRepository.class);
        when(weights.findFirstByIsDefaultTrue()).thenReturn(Optional.of(SolverWeightProfile.builder().maxTimeSeconds(2.0).numSearchWorkers(2).build()));
        var plans = mock(MealPlanRepository.class);
        var stored = new AtomicReference<MealPlan>();
        when(plans.saveAndFlush(any())).thenAnswer(call -> {
            MealPlan plan = call.getArgument(0); plan.setId(77L); plan.setVersion(0L);
            for (int i=0; i<plan.getItems().size(); i++) if (plan.getItems().get(i).getId()==null) plan.getItems().get(i).setId(100L+i);
            stored.set(plan); return plan;
        });
        when(plans.findOwnedForUpdate(77L,99L)).thenAnswer(c -> Optional.of(stored.get()));
        var executor = new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1); executor.initialize();
        try {
            var engine = spy(new CpSatMealOptimizationEngine());
            var service = new MealOptimizerService(weights,mock(MealSlotTemplateRepository.class),plans,
                    mock(CuratedFallbackPlanRepository.class),mock(SolverAuditLogRepository.class),catalog,engine,
                    mock(DiabeticClinicalTargetCalculator.class),new MealPlanArchiveService(mock(MealPlanRevisionRepository.class),new ObjectMapper()),executor);
            service.generateOptimalDailyMealPlan(99L,GenerateDailyMealRequest.builder().planDate(LocalDate.of(2026,11,26))
                    .planningMode(PlanningMode.GENERAL_REFERENCE).targetCaloriesKcal(new BigDecimal("1800")).build());
            var plan = stored.get();
            var breakfast = plan.getItems().stream().filter(item -> item.getMealSlot()==MealSlot.BREAKFAST).toList();
            var request = RebalanceRemainingMealsRequest.builder().alreadyEatenSlots(Set.of(MealSlot.BREAKFAST))
                    .consumedCaloriesKcal(sum(breakfast,MealPlanItem::getSnapshotCaloriesKcal))
                    .consumedNetCarbG(sum(breakfast,MealPlanItem::getSnapshotNetCarbG))
                    .consumedProteinG(sum(breakfast,MealPlanItem::getSnapshotProteinG))
                    .consumedFatG(sum(breakfast,MealPlanItem::getSnapshotFatG))
                    .policy(new MealEditPolicy(0L,Set.of(),Set.of())).build();
            var result = service.rebalanceRemainingMeals(99L,77L,request);
            assertTrue(Set.of(SolverRunStatus.FEASIBLE,SolverRunStatus.OPTIMAL).contains(result.getSolverStatus()));
            assertEquals(breakfast,plan.getItems().stream().filter(item -> item.getMealSlot()==MealSlot.BREAKFAST).toList());
            assertTrue(plan.getActualSodiumMg().compareTo(new BigDecimal("2400"))<=0);
            var contexts = org.mockito.ArgumentCaptor.forClass(CpSatMealOptimizationEngine.SolverInputContext.class);
            verify(engine,times(2)).solveOptimalMealPlan(contexts.capture());
            var rebalance = contexts.getAllValues().getLast();
            assertEquals(PlanningMode.GENERAL_REFERENCE,rebalance.getPlanningMode());
            assertFalse(rebalance.isEvaluateFiber());
            assertEquals(0,new BigDecimal("100").compareTo(rebalance.getActiveSlots().stream().map(MealSlotTemplate::getCalorieRatioPct).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(5,java.math.RoundingMode.HALF_UP)));
            System.out.println("NIN_TOOLS_REBALANCE "+result.getSolverStatus()+" kcal="+plan.getActualCaloriesKcal());
        } finally { executor.shutdown(); }
    }

    private BigDecimal sum(List<MealPlanItem> items,java.util.function.Function<MealPlanItem,BigDecimal> value) {
        return items.stream().map(value).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
}
