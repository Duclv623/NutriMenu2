package com.menufood.userservice.mapper;

import com.menufood.userservice.dto.request.UserProfileRequestDTO;
import com.menufood.userservice.dto.request.NutritionProfileRequestDTO;
import com.menufood.userservice.dto.response.UserProfileResponseDTO;
import com.menufood.userservice.dto.response.NutritionProfileResponseDTO;
import com.menufood.userservice.entity.ActivityLevel;
import com.menufood.userservice.entity.Gender;
import com.menufood.userservice.entity.WeightGoal;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Component
public class NutritionProfileMapper {
    public UserProfileRequestDTO toUserProfile(NutritionProfileRequestDTO input) {
        var profile = new UserProfileRequestDTO();
        profile.setDateOfBirth(input.getDateOfBirth());
        profile.setHeight(input.getHeight());
        profile.setWeight(input.getWeight());
        profile.setTargetWeight(input.getTargetWeight());
        profile.setConfirmed(input.isConfirmed());
        profile.setPregnant(input.isPregnant());
        profile.setSpecialDiet(input.isSpecialDiet());
        profile.setAllergies(input.getAllergies());
        profile.setRestrictedIngredients(input.getExcludedFoods());
        profile.setExpectedVersion(input.getVersion());
        String preferences = input.getPreferences() == null ? "" : input.getPreferences();
        profile.setPreferences(Arrays.stream(preferences.split(",")).map(String::strip)
                .filter(value -> !value.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new)));
        try {
            if (input.getGender() != null) {
                profile.setGender(Gender.valueOf(input.getGender().toUpperCase(Locale.ROOT)));
            }
            profile.setActivityLevel(switch (input.getActivity()) {
                case null -> null;
                case "active" -> ActivityLevel.HIGH;
                case "very_active" -> ActivityLevel.VERY_HIGH;
                default -> ActivityLevel.valueOf(input.getActivity().toUpperCase(Locale.ROOT));
            });
        } catch (IllegalArgumentException invalidEnum) {
            throw new ResponseStatusException(BAD_REQUEST, "Hồ sơ không hợp lệ");
        }
        profile.setGoal(switch (input.getGoal()) {
            case "lose" -> WeightGoal.LOSE_WEIGHT;
            case "maintain" -> WeightGoal.MAINTAIN_WEIGHT;
            case "gain" -> WeightGoal.GAIN_WEIGHT;
            case null -> null;
            default -> throw new ResponseStatusException(BAD_REQUEST, "Hồ sơ không hợp lệ");
        });
        return profile;
    }

    public NutritionProfileResponseDTO toResponse(String name, UserProfileResponseDTO profile) {
        if (profile == null) {
            return new NutritionProfileResponseDTO(name, "", 0, "", BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, "", "", Set.of(), Set.of(), "", false, false, false, 0);
        }
        String activity = switch (profile.getActivityLevel()) {
            case HIGH -> "active";
            case VERY_HIGH -> "very_active";
            default -> profile.getActivityLevel().name().toLowerCase(Locale.ROOT);
        };
        String goal = switch (profile.getGoal()) {
            case LOSE_WEIGHT -> "lose";
            case MAINTAIN_WEIGHT -> "maintain";
            case GAIN_WEIGHT -> "gain";
        };
        return new NutritionProfileResponseDTO(name, profile.getDateOfBirth().toString(), profile.getAge(),
                profile.getGender().name().toLowerCase(Locale.ROOT), profile.getHeight(), profile.getWeight(),
                profile.getTargetWeight() == null ? profile.getWeight() : profile.getTargetWeight(),
                activity, goal, profile.getAllergies(), profile.getRestrictedIngredients(),
                String.join(", ", profile.getPreferences()), profile.isConfirmed(), profile.isPregnant(),
                profile.isSpecialDiet(), profile.getVersion());
    }
}
