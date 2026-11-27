package com.example.mealoptimizerservice.service;

import com.example.mealoptimizerservice.dto.request.GenerateDailyMealRequest;
import com.example.mealoptimizerservice.entity.enums.PlanningMode;
import com.example.mealoptimizerservice.exception.OptimizerBusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class GeneralReferencePolicyTest {
    @Test void omittedModeKeepsClinicalDefaults() {
        var request = GenerateDailyMealRequest.builder().build();
        GeneralReferencePolicy.normalize(request);
        assertEquals(PlanningMode.MEDICAL,request.getPlanningMode());
        assertEquals(0,new BigDecimal("50").compareTo(request.getMaxGiAllowed()));
        assertEquals("STANDARD_4_MEALS",request.getTemplateCode());
    }
    @Test void generalPolicyIsServerOwnedAndDoesNotModifyOtherRequests() {
        var clinical = GenerateDailyMealRequest.builder().build();
        var general = GenerateDailyMealRequest.builder().planningMode(PlanningMode.GENERAL_REFERENCE)
                .targetCaloriesKcal(new BigDecimal("1800")).maxDailySodiumMg(new BigDecimal("99999")).build();
        GeneralReferencePolicy.normalize(general);
        assertEquals(GeneralReferencePolicy.TEMPLATE,general.getTemplateCode());
        assertEquals(new BigDecimal("100"),general.getMaxGiAllowed());
        assertEquals(new BigDecimal("2400"),general.getMaxDailySodiumMg());
        assertEquals(new BigDecimal("50.0"),clinical.getMaxGiAllowed());
        assertEquals(PlanningMode.MEDICAL,clinical.getPlanningMode());
    }
    @Test void medicalCannotSelectGeneralTemplateOrRelaxGi() {
        var request=GenerateDailyMealRequest.builder().templateCode(GeneralReferencePolicy.TEMPLATE).build();
        assertThrows(OptimizerBusinessException.class,()->GeneralReferencePolicy.normalize(request));
        request.setTemplateCode("STANDARD_4_MEALS"); request.setMaxGiAllowed(new BigDecimal("100"));
        assertThrows(OptimizerBusinessException.class,()->GeneralReferencePolicy.normalize(request));
    }
    @Test void nullModeIsRejected() {
        var request=GenerateDailyMealRequest.builder().planningMode(null).build();
        assertThrows(OptimizerBusinessException.class,()->GeneralReferencePolicy.normalize(request));
    }
    @Test void regeneratingCannotReplaceADayUsingAnotherMode() {
        var plans=org.mockito.Mockito.mock(com.example.mealoptimizerservice.dao.repository.MealPlanRepository.class);
        var catalog=org.mockito.Mockito.mock(com.example.mealoptimizerservice.client.FoodCatalogIntegrationClient.class);
        var date=java.time.LocalDate.of(2026,11,24);
        var existing=com.example.mealoptimizerservice.entity.MealPlan.builder().planningMode(PlanningMode.MEDICAL).build();
        org.mockito.Mockito.when(plans.findByUserIdAndPlanDate(1L,date)).thenReturn(java.util.Optional.of(existing));
        var service=new MealOptimizerService(null,null,plans,null,null,catalog,null,null,null,null);
        var request=GenerateDailyMealRequest.builder().planDate(date).planningMode(PlanningMode.GENERAL_REFERENCE).build();
        var error=assertThrows(OptimizerBusinessException.class,()->service.generateOptimalDailyMealPlan(1L,request));
        assertEquals(org.springframework.http.HttpStatus.CONFLICT,error.getStatus());
        org.mockito.Mockito.verifyNoInteractions(catalog);
    }
}
