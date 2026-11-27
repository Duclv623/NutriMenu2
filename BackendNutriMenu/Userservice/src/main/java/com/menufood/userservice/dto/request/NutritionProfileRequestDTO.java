package com.menufood.userservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@JsonIgnoreProperties({"name", "age"})
public class NutritionProfileRequestDTO {
    private LocalDate dateOfBirth;
    private String gender;
    private BigDecimal height;
    private BigDecimal weight;
    private BigDecimal targetWeight;
    private String activityLevel;
    private String goal;
    private Set<String> allergies = new LinkedHashSet<>();
    private Set<String> excludedFoods;
    private String preferences;
    private boolean confirmed;
    private boolean pregnant;
    private boolean specialDiet;
    private Long version;
}
