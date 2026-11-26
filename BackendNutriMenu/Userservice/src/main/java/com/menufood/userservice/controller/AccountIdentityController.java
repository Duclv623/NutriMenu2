package com.menufood.userservice.controller;

import com.menufood.userservice.dto.response.NutritionProfileResponseDTO;
import com.menufood.userservice.dao.repository.AccountStateRepository;
import com.menufood.userservice.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal")
public class AccountIdentityController {
    private final AccountStateRepository accounts;
    private final CurrentUserService current;
    private final NutritionProfileService profiles;

    public record Identity(String owner, long serviceUserId) {}
    public record MealAccount(long serviceUserId, NutritionProfileResponseDTO profile, Map<String, JsonNode> previousPlans) {}

    @GetMapping("/identity")
    @Transactional
    public Identity identity(Authentication auth) {
        var account = accounts.load(current.id(auth));
        return new Identity("user:" + account.id(), account.serviceId());
    }

    @GetMapping("/meal-account")
    @Transactional
    public MealAccount mealAccount(Authentication auth) {
        var account = accounts.load(current.id(auth));
        // Plans created before the optimizer stored the profile version locally.
        Map<String, JsonNode> previousPlans = new HashMap<>();
        account.state().properties().forEach(e -> {
            if (e.getKey().startsWith("plan:")) previousPlans.put(e.getKey().substring(5), e.getValue());
        });
        return new MealAccount(account.serviceId(), profiles.profile(current.email(auth)), previousPlans);
    }
}
