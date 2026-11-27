package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.client.MealAccountClient.*;
import com.example.mealoptimizerservice.dao.repository.MealPlanRepository;
import com.example.mealoptimizerservice.dto.request.*;
import com.example.mealoptimizerservice.dto.response.MealPlanResponse;
import com.example.mealoptimizerservice.entity.MealPlan;
import com.example.mealoptimizerservice.entity.enums.*;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import com.example.mealoptimizerservice.mapper.MealPlanMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MealPlanToolsServiceTest {
    final LocalDate start = LocalDate.of(2026,11,26);
    final PersonalMealPlanService personal = mock(PersonalMealPlanService.class);
    final MealOptimizerService optimizer = mock(MealOptimizerService.class);
    final MealPlanRepository plans = mock(MealPlanRepository.class);
    final Profile profile = new Profile(30,"female",new BigDecimal("160"),new BigDecimal("60"),"light","maintain",
            Set.of("Trứng"),Set.of("Đậu"),true,false,false,3L);
    MealPlanToolsService service;

    @BeforeEach void setup() {
        when(personal.accountForUpdate("Bearer test")).thenReturn(new MealAccount(99L,profile,Map.of()));
        when(personal.exclusions("Bearer test",profile)).thenAnswer(c -> new HashSet<>(Set.of(8L)));
        when(personal.allergyTags(profile)).thenAnswer(c -> new HashSet<>(Set.of(MedicalTag.ALLERGEN_EGG)));
        service = new MealPlanToolsService(personal,optimizer,mock(CheckedMealChangeService.class),plans,
                mock(MealPlanMapper.class),mock(DiabeticClinicalTargetCalculator.class),Validation.buildDefaultValidatorFactory().getValidator());
    }
    GenerateWeeklyMealRequest request() {
        return GenerateWeeklyMealRequest.builder().startDate(start).dailyTargetTemplate(GenerateDailyMealRequest.builder()
                .planningMode(PlanningMode.GENERAL_REFERENCE).targetCaloriesKcal(new BigDecimal("1800"))
                .targetNetCarbG(new BigDecimal("247.5")).targetProteinG(new BigDecimal("78.75"))
                .targetFatG(new BigDecimal("55")).excludedDishIds(Set.of(9L))
                .patientContraindications(Set.of(MedicalTag.ALLERGEN_SOY)).build()).build();
    }
    List<MealPlanResponse> results() {
        return java.util.stream.IntStream.range(0,7).mapToObj(day -> MealPlanResponse.builder()
                .planId(100L+day).planDate(start.plusDays(day)).solverStatus(SolverRunStatus.FEASIBLE).build()).toList();
    }
    @Test void weeklyMergesProfileRestrictionsAndRechecksTheProfileAfterSolving() {
        var input = request();
        when(optimizer.generateWeeklyMealPlans(99L,input)).thenReturn(results());
        assertEquals(7,service.weekly("Bearer test",3L,input).size());
        assertEquals(Set.of(8L,9L),input.getDailyTargetTemplate().getExcludedDishIds());
        assertEquals(Set.of(MedicalTag.ALLERGEN_EGG,MedicalTag.ALLERGEN_SOY),input.getDailyTargetTemplate().getPatientContraindications());
        assertEquals(3L,input.getDailyTargetTemplate().getSourceProfileVersion());
        assertEquals(PlanningMode.GENERAL_REFERENCE,input.getDailyTargetTemplate().getPlanningMode());
        verify(personal,times(2)).eligible(profile,3L);
    }
    @Test void occupiedDatePreventsWeeklySolverFromBeingCalled() {
        when(plans.findByUserIdAndPlanDate(99L,start.plusDays(4))).thenReturn(Optional.of(new MealPlan()));
        assertThrows(OptimizerBusinessException.class,() -> service.weekly("Bearer test",3L,request()));
        verifyNoInteractions(optimizer);
    }
    @Test void incompleteOrUnusableWeekThrowsWithinTheTransaction() {
        var input = request();
        when(optimizer.generateWeeklyMealPlans(99L,input)).thenReturn(results().subList(0,6));
        assertThrows(OptimizerBusinessException.class,() -> service.weekly("Bearer test",3L,input));
        var all = results(); all.get(5).setSolverStatus(SolverRunStatus.UNKNOWN);
        when(optimizer.generateWeeklyMealPlans(99L,input)).thenReturn(all);
        assertThrows(OptimizerBusinessException.class,() -> service.weekly("Bearer test",3L,input));
        assertTrue(MealPlanToolsService.class.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }
}
