package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.client.*;
import com.example.mealoptimizerservice.client.MealAccountClient.*;
import com.example.mealoptimizerservice.dao.repository.MealPlanRepository;
import com.example.mealoptimizerservice.dto.request.*;
import com.example.mealoptimizerservice.dto.response.*;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.mapper.MealPlanMapper;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalMealPlanServiceTest {
    MealAccountClient accounts;
    MealOptimizerService optimizer;
    MealPlanRepository plans;
    PersonalMealPlanService service;
    final LocalDate date = LocalDate.of(2026, 11, 26);
    @BeforeEach void setup() {
        accounts = mock(MealAccountClient.class);
        optimizer = mock(MealOptimizerService.class);
        plans = mock(MealPlanRepository.class);
        var profile = new Profile(30,"female",new BigDecimal("160"),new BigDecimal("60"),"light","maintain",
                Set.of(),Set.of(),true,false,false,3L);
        when(accounts.account("Bearer test")).thenReturn(new MealAccount(99L,profile,Map.of()));
        when(optimizer.generateOptimalDailyMealPlan(eq(99L),any())).thenReturn(raw(3L));
        service = new PersonalMealPlanService(accounts,mock(FoodCatalogIntegrationClient.class),optimizer,
                mock(CheckedMealChangeService.class),plans,new MealPlanMapper(new ObjectMapper()),
                mock(JdbcTemplate.class),mock(Validator.class));
    }
    MealPlanResponse raw(long sourceVersion) {
        return MealPlanResponse.builder().planId(7L).userId(99L).version(4L).sourceProfileVersion(sourceVersion)
                .planDate(date).planningMode(PlanningMode.GENERAL_REFERENCE).solverStatus(SolverRunStatus.FEASIBLE)
                .targetCaloriesKcal(new BigDecimal("1800")).mealsBySlot(Map.of()).medicalWarnings(List.of()).build();
    }
    GenerateMealPlanRequestDTO request(Long id,Long version,boolean confirmed) {
        return new GenerateMealPlanRequestDTO(date,1800,true,0,false,3L,PlanningMode.GENERAL_REFERENCE,id,version,confirmed);
    }
    @Test void forwardsModeAndCurrentProfileVersion() {
        var result = service.generate("Bearer test",request(null,null,false));
        var capture = ArgumentCaptor.forClass(GenerateDailyMealRequest.class);
        verify(optimizer).generateOptimalDailyMealPlan(eq(99L),capture.capture());
        assertEquals(PlanningMode.GENERAL_REFERENCE,capture.getValue().getPlanningMode());
        assertEquals(3L,capture.getValue().getSourceProfileVersion());
        assertEquals("GENERAL_REFERENCE",result.planningMode());
        assertEquals(3L,result.profileVersion());
    }
    @Test void replacementPreservesOwnerAndConvertsFrontendVersion() {
        when(optimizer.getMealPlanDetail(99L,7L)).thenReturn(raw(2L));
        service.generate("Bearer test",request(7L,5L,true));
        var capture = ArgumentCaptor.forClass(GenerateDailyMealRequest.class);
        verify(optimizer).generateOptimalDailyMealPlan(eq(99L),capture.capture());
        assertEquals(7L,capture.getValue().getReplacePlanId());
        assertEquals(4L,capture.getValue().getExpectedVersion());
        assertEquals(2L,capture.getValue().getPreviousProfileVersion());
        assertTrue(capture.getValue().isReplacementConfirmed());
    }
    @Test void rejectsUnconfirmedReplacementBeforeCallingSolver() {
        assertThrows(OptimizerBusinessException.class,() -> service.generate("Bearer test",request(7L,5L,false)));
        verify(optimizer,never()).generateOptimalDailyMealPlan(anyLong(),any());
    }
    @Test void rejectsStaleReplacementVersion() {
        when(optimizer.getMealPlanDetail(99L,7L)).thenReturn(raw(2L));
        assertThrows(OptimizerBusinessException.class,() -> service.generate("Bearer test",request(7L,4L,true)));
        verify(optimizer,never()).generateOptimalDailyMealPlan(anyLong(),any());
    }

    @Test void weeklyUsesTheSameOwnerProfileAndAllSevenDates() {
        var weekly = java.util.stream.IntStream.range(0,7).mapToObj(index -> {
            var day = raw(3L); day.setPlanId(7L+index); day.setPlanDate(date.plusDays(index)); return day;
        }).toList();
        when(optimizer.generateWeeklyMealPlans(eq(99L),any())).thenReturn(weekly);
        var results = service.generateWeekly("Bearer test",request(null,null,false));
        assertEquals(7,results.size());
        for (int index=0; index<7; index++) {
            assertEquals(date.plusDays(index).toString(),results.get(index).date());
            assertEquals(3L,results.get(index).profileVersion());
            assertEquals("GENERAL_REFERENCE",results.get(index).planningMode());
        }
        var capture = ArgumentCaptor.forClass(GenerateWeeklyMealRequest.class);
        verify(optimizer).generateWeeklyMealPlans(eq(99L),capture.capture());
        assertEquals(3L,capture.getValue().getDailyTargetTemplate().getSourceProfileVersion());
        assertEquals(PlanningMode.GENERAL_REFERENCE,capture.getValue().getDailyTargetTemplate().getPlanningMode());
    }

    @Test void weeklyRefusesAnOccupiedDateBeforeSolvingOrOverwritingAnything() {
        when(plans.findByUserIdAndPlanDateBetweenOrderByPlanDateAsc(99L,date,date.plusDays(6)))
                .thenReturn(List.of(com.example.mealoptimizerservice.entity.MealPlan.builder().planDate(date.plusDays(2)).build()));
        assertThrows(OptimizerBusinessException.class,() -> service.generateWeekly("Bearer test",request(null,null,false)));
        verify(optimizer,never()).generateWeeklyMealPlans(anyLong(),any());
        verify(plans,never()).delete(any());
    }

    @Test void weeklyRejectsReplacementAndIncompleteSolverResults() {
        assertThrows(OptimizerBusinessException.class,() -> service.generateWeekly("Bearer test",request(7L,5L,true)));
        verify(optimizer,never()).generateWeeklyMealPlans(anyLong(),any());
        when(optimizer.generateWeeklyMealPlans(eq(99L),any())).thenReturn(List.of(raw(3L)));
        assertThrows(OptimizerBusinessException.class,() -> service.generateWeekly("Bearer test",request(null,null,false)));
    }
}
