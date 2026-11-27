package com.example.mealoptimizerservice.controller;

import com.example.mealoptimizerservice.client.MealAccountClient;
import com.example.mealoptimizerservice.dto.request.GenerateMealPlanRequestDTO;
import com.example.mealoptimizerservice.exception.GlobalExceptionHandler;
import com.example.mealoptimizerservice.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import com.example.mealoptimizerservice.dto.request.SwapMealRequestDTO;
import com.example.mealoptimizerservice.dto.response.PersonalMealPlanResponseDTO;
import com.example.mealoptimizerservice.dto.response.PersonalMealPlanResponseDTO.Item;
import com.example.mealoptimizerservice.dto.response.PersonalMealPlanResponseDTO.Nutrition;
import java.math.BigDecimal;
import java.util.List;
import tools.jackson.databind.ObjectMapper;

class PersonalMealPlanHttpTest {
    @Test void acceptsTheExactPersonalFrontendGenerationPayload() throws Exception {
        var service = mock(PersonalMealPlanService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new MealPlanController(service,mock(MealAccountClient.class),mock(MealOptimizerService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(post("/api/meal-plans/generate").header("Authorization","Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"planningMode":"GENERAL_REFERENCE","date":"2026-11-26","target":1800,"snacks":true,
                "maxMinutes":0,"excludeRepeat":false,"profileVersion":3}
                """)).andReturn().getResponse();
        assertEquals(200,response.getStatus(),response.getContentAsString());
        verify(service).generate(eq("Bearer test"),any(GenerateMealPlanRequestDTO.class));
        var input = org.mockito.ArgumentCaptor.forClass(GenerateMealPlanRequestDTO.class);
        verify(service).generate(eq("Bearer test"),input.capture());
        assertFalse(input.getValue().replacementConfirmed());
    }

    @Test void weeklyAcceptsTheSameDefaultsAndRoutesToWeeklyService() throws Exception {
        var service = mock(PersonalMealPlanService.class);
        when(service.generateWeekly(anyString(),any())).thenReturn(java.util.List.of());
        var mvc = MockMvcBuilders.standaloneSetup(new MealPlanController(service,mock(MealAccountClient.class),mock(MealOptimizerService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(post("/api/meal-plans/generate-weekly").header("Authorization","Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"planningMode":"GENERAL_REFERENCE","date":"2026-11-26","target":1800,"snacks":true,
                "maxMinutes":0,"excludeRepeat":false,"profileVersion":3}
                """)).andReturn().getResponse();
        assertEquals(200,response.getStatus(),response.getContentAsString());
        verify(service).generateWeekly(eq("Bearer test"),any());
    }

    @Test void generatePreviewAndApplyUseTheCanonicalDishAndPortionFields() throws Exception {
        var service = mock(PersonalMealPlanService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new MealPlanController(service,mock(MealAccountClient.class),mock(MealOptimizerService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var nutrition = new Nutrition(BigDecimal.valueOf(400), BigDecimal.valueOf(50), BigDecimal.TEN, BigDecimal.ONE, null, null);
        var before = new PersonalMealPlanResponseDTO("7","2026-11-26",3,1,
                List.of(new Item("Món NIN","1","9","lunch",BigDecimal.ONE,false,false,nutrition,"UNSPECIFIED_SOURCE_GLUCID")),
                "GENERAL_REFERENCE","FEASIBLE","api",BigDecimal.valueOf(1800),List.of(),List.of(),null,null,"",true,"");
        var after = new PersonalMealPlanResponseDTO("7","2026-11-26",3,2,
                List.of(new Item("Món thay thế","1","10","lunch",BigDecimal.valueOf(12),false,false,nutrition,"UNSPECIFIED_SOURCE_GLUCID")),
                "GENERAL_REFERENCE","FEASIBLE","api",BigDecimal.valueOf(1800),List.of(),List.of(),null,null,"",true,"checked");
        when(service.generate(eq("Bearer test"),any())).thenReturn(before);
        when(service.change(eq("Bearer test"),eq(7L),any(),anyBoolean())).thenReturn(after);
        var json = new ObjectMapper();
        var generated = mvc.perform(post("/api/meal-plans/generate").header("Authorization","Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"planningMode":"GENERAL_REFERENCE","date":"2026-11-26","target":1800,"snacks":true,"maxMinutes":0,"excludeRepeat":false,"profileVersion":3}
                """)).andReturn().getResponse();
        assertEquals(200, generated.getStatus(), generated.getContentAsString());
        var item = json.readTree(generated.getContentAsString()).path("items").get(0);
        assertEquals("9", item.path("dishId").asText());
        assertEquals(1, item.path("servingMultiplier").asInt());
        assertEquals(400, item.path("nutrition").path("energyKcal").asInt());

        String swap = """
                {"baseVersion":1,"profileVersion":3,"itemId":"1","dishId":"10","servingMultiplier":12,"previewSignature":"checked"}
                """;
        for (String endpoint : List.of("swap-preview", "swap")) {
            var response = mvc.perform(post("/api/meal-plans/7/" + endpoint).header("Authorization","Bearer test")
                    .contentType(MediaType.APPLICATION_JSON).content(swap)).andReturn().getResponse();
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals("10", json.readTree(response.getContentAsString()).path("items").get(0).path("dishId").asText());
        }
        var outside = mvc.perform(post("/api/meal-plans/7/swap-preview").header("Authorization","Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content(swap.replace("12", "12.5"))).andReturn().getResponse();
        assertEquals(400, outside.getStatus(), outside.getContentAsString());
        var request = new SwapMealRequestDTO(1, 3, "1", "10", 12, "checked");
        verify(service).change("Bearer test", 7L, request, true);
        verify(service).change("Bearer test", 7L, request, false);
    }

    @Test void malformedJsonReturnsAnActionableMessage() throws Exception {
        var service = mock(PersonalMealPlanService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new MealPlanController(service,mock(MealAccountClient.class),mock(MealOptimizerService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var response = mvc.perform(post("/api/meal-plans/generate").header("Authorization","Bearer test")
                .contentType(MediaType.APPLICATION_JSON).content("{bad json")).andReturn().getResponse();
        assertEquals(400,response.getStatus());
        assertTrue(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("yyyy-MM-dd"));
        verifyNoInteractions(service);
    }
}
