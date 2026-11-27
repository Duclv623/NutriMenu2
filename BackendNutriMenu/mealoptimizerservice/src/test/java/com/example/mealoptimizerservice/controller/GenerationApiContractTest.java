package com.example.mealoptimizerservice.controller;

import com.example.mealoptimizerservice.dto.request.GenerateDailyMealRequest;
import com.example.mealoptimizerservice.dto.response.MealPlanResponse;
import com.example.mealoptimizerservice.service.GeneralReferencePolicy;
import com.example.mealoptimizerservice.service.MealOptimizerService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class GenerationApiContractTest {
    @Test void bffMedicalJsonKeepsDefaultsWhenOptionalLimitsAreOmitted() throws Exception {
        var service=mock(MealOptimizerService.class);
        var received=new AtomicReference<GenerateDailyMealRequest>();
        when(service.generateOptimalDailyMealPlan(eq(1L),any())).thenAnswer(call -> {
            GenerateDailyMealRequest request=call.getArgument(1); received.set(request);
            return MealPlanResponse.builder().planId(1L).build();
        });
        var mvc=MockMvcBuilders.standaloneSetup(new UserMealPlanController(service)).build();
        var result=mvc.perform(post("/api/v1/users/me/meal-plans/generate-daily").contentType(MediaType.APPLICATION_JSON).content("""
            {"planningMode":"MEDICAL","planDate":"2026-11-24","targetCaloriesKcal":1800,
             "targetNetCarbG":247.5,"targetProteinG":78.8,"targetFatG":55,
             "patientContraindications":[],"excludedDishIds":[],"templateCode":"STANDARD_4_MEALS","enableRandomPerturbation":true}
            """)).andReturn();
        assertEquals(201,result.getResponse().getStatus());
        assertNotNull(received.get());
        assertEquals(new BigDecimal("50.0"),received.get().getMaxGiAllowed(),"HTTP-deserialized omitted GI");
        assertEquals(new BigDecimal("80.0"),received.get().getMaxDailyGl(),"HTTP-deserialized omitted GL");
        GeneralReferencePolicy.normalize(received.get());
    }
}
