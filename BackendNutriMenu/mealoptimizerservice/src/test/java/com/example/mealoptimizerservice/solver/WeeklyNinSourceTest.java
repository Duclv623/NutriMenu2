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
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeeklyNinSourceTest {
    @Test void runsAllSevenDaysThroughTheExistingWeeklySolverUsingRealNinEggSoyCandidates() throws Exception {
        Loader.loadNativeLibraries();
        var candidates = NinBulkSourceDatasetTest.sourceCandidates(true);
        var catalog = mock(FoodCatalogIntegrationClient.class);
        when(catalog.fetchCandidatesForSolver(any(),any(),anySet(),anySet(),anySet(),any())).thenReturn(candidates);
        var weights = mock(SolverWeightProfileRepository.class);
        when(weights.findFirstByIsDefaultTrue()).thenReturn(Optional.of(SolverWeightProfile.builder().maxTimeSeconds(1.5).numSearchWorkers(2).build()));
        var plans = mock(MealPlanRepository.class);
        var ids = new AtomicLong(100);
        when(plans.saveAndFlush(any())).thenAnswer(call -> {
            MealPlan plan = call.getArgument(0); plan.setId(ids.incrementAndGet()); plan.setVersion(0L); return plan;
        });
        var executor = new ThreadPoolTaskExecutor(); executor.setCorePoolSize(1); executor.initialize();
        try {
            var service = new MealOptimizerService(weights,mock(MealSlotTemplateRepository.class),plans,
                    mock(CuratedFallbackPlanRepository.class),mock(SolverAuditLogRepository.class),catalog,new CpSatMealOptimizationEngine(),
                    mock(DiabeticClinicalTargetCalculator.class),new MealPlanArchiveService(mock(MealPlanRevisionRepository.class),new ObjectMapper()),executor);
            var start = LocalDate.of(2026,11,26);
            var template = GenerateDailyMealRequest.builder().planningMode(PlanningMode.GENERAL_REFERENCE).sourceProfileVersion(3L)
                    .targetCaloriesKcal(new BigDecimal("1800")).patientContraindications(Set.of(MedicalTag.ALLERGEN_EGG,MedicalTag.ALLERGEN_SOY)).build();
            var results = service.generateWeeklyMealPlans(99L,GenerateWeeklyMealRequest.builder().startDate(start).dailyTargetTemplate(template).build());
            assertEquals(7,results.size());
            for (int day=0; day<7; day++) {
                var plan = results.get(day);
                assertEquals(start.plusDays(day),plan.getPlanDate());
                assertEquals(99L,plan.getUserId()); assertEquals(3L,plan.getSourceProfileVersion());
                assertEquals(PlanningMode.GENERAL_REFERENCE,plan.getPlanningMode());
                assertTrue(Set.of(SolverRunStatus.FEASIBLE,SolverRunStatus.OPTIMAL).contains(plan.getSolverStatus()));
                assertTrue(plan.getActualSodiumMg().compareTo(new BigDecimal("2400"))<=0);
                assertTrue(plan.getMealsBySlot().values().stream().flatMap(List::stream).allMatch(item -> item.getDishCode().startsWith("NIN_BULK_")));
                System.out.println("WEEKLY_NIN "+plan.getPlanDate()+" "+plan.getSolverStatus()+" kcal="+plan.getActualCaloriesKcal());
            }
            verify(plans,times(7)).saveAndFlush(any());
            verify(plans,never()).delete(any());
        } finally { executor.shutdown(); }
    }
}
