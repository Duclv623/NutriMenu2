package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.client.FoodCatalogIntegrationClient;
import com.example.mealoptimizerservice.dto.DishSolverDto;
import com.example.mealoptimizerservice.dto.request.GenerateDailyMealRequest;
import com.example.mealoptimizerservice.entity.MealSlotTemplate;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import com.example.mealoptimizerservice.dao.repository.*;
import com.example.mealoptimizerservice.solver.CpSatMealOptimizationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NewSourceCatalogPolicyTest {
    @Test void snackOnlySourceDataStopsBeforeSolverAndNeverUsesCuratedFallback() {
        var templates = mock(MealSlotTemplateRepository.class);
        var catalog = mock(FoodCatalogIntegrationClient.class);
        var engine = mock(CpSatMealOptimizationEngine.class);
        var fallback = mock(CuratedFallbackPlanRepository.class);
        var plans = mock(MealPlanRepository.class);
        when(templates.findByTemplateCodeOrderByIdAsc("STANDARD_4_MEALS")).thenReturn(List.of(
                MealSlotTemplate.builder().mealSlot(MealSlot.BREAKFAST).allowCompositeDish(true).build(),
                MealSlotTemplate.builder().mealSlot(MealSlot.LUNCH).reqMainStarch(1).reqProteinDish(1)
                        .reqVeggieDish(1).reqSoup(1).build()));
        when(catalog.fetchCandidatesForSolver(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                DishSolverDto.builder().dishId(1L).code("NIN_BULK_FRUIT").role(DishRole.SNACK)
                        .validMealSlots(Set.of(MealSlot.AFTERNOON_SNACK)).build(),
                DishSolverDto.builder().dishId(2L).code("QA_BREAKFAST").role(DishRole.COMPOSITE_DISH)
                        .validMealSlots(Set.of(MealSlot.BREAKFAST)).build()));
        var service = new MealOptimizerService(mock(SolverWeightProfileRepository.class), templates, plans,
                fallback, mock(SolverAuditLogRepository.class), catalog, engine,
                mock(DiabeticClinicalTargetCalculator.class), null, mock(ThreadPoolTaskExecutor.class));
        var request = GenerateDailyMealRequest.builder().build();
        var error = assertThrows(OptimizerBusinessException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "runOptimizationPipelineWithExtraExclusions", 12L, request, Set.of(), false, "TEST"));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, error.getStatus());
        for (String role : List.of("bữa sáng", "món tinh bột", "món đạm", "món rau", "món canh"))
            assertTrue(error.getMessage().contains(role), error.getMessage());
        assertFalse(error.getMessage().contains("MAIN_STARCH"));
        verifyNoInteractions(engine, fallback);
        verify(plans, never()).saveAndFlush(any());
    }
}
