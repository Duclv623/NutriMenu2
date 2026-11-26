package com.menufood.userservice.dto.response;

import java.math.BigDecimal;
import java.util.Set;

public record NutritionProfileResponseDTO(
        String name, String dateOfBirth, int age, String gender,
        BigDecimal height, BigDecimal weight, BigDecimal targetWeight,
        String activity, String goal, Set<String> allergies, Set<String> excludedFoods,
        String preferences, boolean confirmed, boolean pregnant, boolean specialDiet, long version) {
}
