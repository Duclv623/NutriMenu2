package com.menufood.userservice.mapper;

import com.menufood.userservice.dto.request.UserProfileRequestDTO;
import com.menufood.userservice.dto.response.UserProfileResponseDTO;
import com.menufood.userservice.entity.UserProfile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserProfileMapper {
    public void update(UserProfile profile, UserProfileRequestDTO request) {
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setGender(request.getGender());
        profile.setHeight(request.getHeight());
        profile.setWeight(request.getWeight());
        profile.setActivityLevel(request.getActivityLevel());
        profile.setGoal(request.getGoal());
        profile.setTargetWeight(request.getTargetWeight());
        profile.setConfirmed(request.isConfirmed());
        profile.setPregnant(request.isPregnant());
        profile.setSpecialDiet(request.isSpecialDiet());
        profile.getAllergies().clear();
        profile.getAllergies().addAll(normalize(request.getAllergies()));
        profile.getRestrictedIngredients().clear();
        profile.getRestrictedIngredients().addAll(normalize(request.getRestrictedIngredients()));
        profile.getPreferences().clear();
        profile.getPreferences().addAll(normalize(request.getPreferences()));
    }

    private Set<String> normalize(Set<String> values) {
        return values.stream().map(String::strip)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public UserProfileResponseDTO toResponse(UserProfile profile) {
        return UserProfileResponseDTO.builder()
                .userId(profile.getUserId())
                .dateOfBirth(profile.getDateOfBirth())
                .age(Period.between(profile.getDateOfBirth(), LocalDate.now()).getYears())
                .gender(profile.getGender())
                .height(profile.getHeight())
                .weight(profile.getWeight())
                .activityLevel(profile.getActivityLevel())
                .goal(profile.getGoal())
                .targetWeight(profile.getTargetWeight())
                .confirmed(profile.isConfirmed())
                .pregnant(profile.isPregnant())
                .specialDiet(profile.isSpecialDiet())
                .version(profile.getVersion())
                .allergies(new LinkedHashSet<>(profile.getAllergies()))
                .restrictedIngredients(new LinkedHashSet<>(profile.getRestrictedIngredients()))
                .preferences(new LinkedHashSet<>(profile.getPreferences()))
                .updatedAt(profile.getUpdatedAt())
                .build();
    }
}
