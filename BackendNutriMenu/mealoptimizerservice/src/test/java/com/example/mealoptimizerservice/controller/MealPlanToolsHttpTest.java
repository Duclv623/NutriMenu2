package com.example.mealoptimizerservice.controller;

import com.example.mealoptimizerservice.client.MealAccountClient;
import com.example.mealoptimizerservice.dto.request.GenerateWeeklyMealRequest;
import com.example.mealoptimizerservice.entity.enums.PlanningMode;
import com.example.mealoptimizerservice.service.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class MealPlanToolsHttpTest {
    @Test void weeklyFormUsesJwtOwnerAndDeserializesOptionalDailyDefaults() throws Exception {
        var tools = mock(MealPlanToolsService.class);
        when(tools.weekly(eq("Bearer test"),eq(3L),any())).thenReturn(List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new MealPlanToolsController(tools,
                mock(MealOptimizerService.class),mock(MealAccountClient.class))).build();
        var result = mvc.perform(post("/api/meal-plans/tools/users/me/meal-plans/generate-weekly")
                .param("profileVersion","3").header("Authorization","Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"startDate":"2026-11-26","dailyTargetTemplate":{"planningMode":"GENERAL_REFERENCE",
                 "targetCaloriesKcal":1800,"targetNetCarbG":247.5,"targetProteinG":78.75,"targetFatG":55}}
                """)).andReturn();
        assertEquals(200,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        var captured = ArgumentCaptor.forClass(GenerateWeeklyMealRequest.class);
        verify(tools).weekly(eq("Bearer test"),eq(3L),captured.capture());
        var daily = captured.getValue().getDailyTargetTemplate();
        assertEquals(PlanningMode.GENERAL_REFERENCE,daily.getPlanningMode());
        assertEquals(new BigDecimal("50.0"),daily.getMaxGiAllowed());
        assertEquals(new BigDecimal("80.0"),daily.getMaxDailyGl());
    }
}
