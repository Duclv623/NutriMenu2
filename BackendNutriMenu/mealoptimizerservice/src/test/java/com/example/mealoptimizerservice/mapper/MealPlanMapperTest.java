package com.example.mealoptimizerservice.mapper;

import com.example.mealoptimizerservice.client.MealAccountClient.Profile;
import com.example.mealoptimizerservice.dto.response.PersonalMealPlanResponseDTO;
import com.example.mealoptimizerservice.dto.response.PersonalMealPlanResponseDTO.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MealPlanMapperTest {
    @Test void snapshotPreservesUnknownNutrientsAndGeneralPlanningMode() {
        var json = new ObjectMapper();
        var nutrition = new Nutrition(new BigDecimal("400"),new BigDecimal("50"),new BigDecimal("20"),new BigDecimal("10"),null,new BigDecimal("200"));
        var plan = new PersonalMealPlanResponseDTO("7","2026-11-26",3,5,
                List.of(new Item("Món NIN","1","9","lunch",BigDecimal.ONE,false,false,nutrition,"UNSPECIFIED_SOURCE_GLUCID")),"GENERAL_REFERENCE",
                "FEASIBLE","api",new BigDecimal("1800"),List.of(),List.of(),null,null,"",false,"");
        var profile = new Profile(30,"female",new BigDecimal("160"),new BigDecimal("60"),"light","maintain",Set.of(),Set.of(),true,false,false,3);
        var dish = json.readTree("{\"nameVi\":\"Món NIN\",\"code\":\"NIN_BULK_TEST\",\"recipeBreakdown\":[{\"ingredientId\":1,\"ingredientNameVi\":\"Gạo\"}]}");
        ((tools.jackson.databind.node.ObjectNode) dish).put("nutritionQualityJson", "{\"nonQuantifiedSourceAnnotations\":[{\"name_vi\":\"Nước dùng\"}]}");
        var snapshot = new MealPlanMapper(json).snapshot(profile,plan,Map.of("9",dish));
        assertTrue(snapshot.path("plan").path("totals").path("fiberG").isNull());
        assertEquals(400,snapshot.path("plan").path("totals").path("energyKcal").asInt());
        assertEquals("GENERAL_REFERENCE",snapshot.path("plan").path("planningMode").asText());
        assertEquals("unknown",snapshot.path("plan").path("items").get(0).path("nutritionBasis").asText());
        assertTrue(snapshot.path("plan").path("items").get(0).path("sourceRefs").get(0).asText().contains("NIN_BULK_TEST"));
        assertEquals("UNSPECIFIED_SOURCE_GLUCID",snapshot.path("plan").path("items").get(0).path("carbohydrateDefinition").asText());
        assertEquals(2,snapshot.path("plan").path("items").get(0).path("ingredients").size());
        assertFalse(snapshot.path("plan").path("items").get(0).path("ingredientsComplete").asBoolean());
    }
}
