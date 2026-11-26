package com.example.mealoptimizerservice.controller;

import com.example.mealoptimizerservice.dto.request.*;
import com.example.mealoptimizerservice.dto.response.*;
import com.example.mealoptimizerservice.client.MealAccountClient;
import com.example.mealoptimizerservice.service.MealPlanToolsService;
import com.example.mealoptimizerservice.service.MealOptimizerService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/meal-plans/tools")
@RequiredArgsConstructor
public class MealPlanToolsController {
    private static final String PLANS = "/users/me/meal-plans";
    private final MealPlanToolsService tools;
    private final MealOptimizerService optimizer;
    private final MealAccountClient accounts;

    @PostMapping(PLANS + "/generate-daily")
    public MealPlanResponse daily(@RequestHeader("Authorization") String token,
            @RequestParam long profileVersion,
            @RequestBody GenerateDailyMealRequest input) {
        return tools.daily(token, profileVersion, input);
    }

    @PostMapping(PLANS + "/generate-weekly")
    public List<MealPlanResponse> weekly(@RequestHeader("Authorization") String token,
            @RequestParam long profileVersion,
            @RequestBody GenerateWeeklyMealRequest input) {
        return tools.weekly(token, profileVersion, input);
    }

    @PostMapping(PLANS + "/generate-from-profile")
    public Map<String,Object> fromProfile(@RequestHeader("Authorization") String token,
            @RequestParam long profileVersion,
            @RequestBody PatientProfileMealRequest input) {
        return tools.fromProfile(token, profileVersion, input);
    }

    @PostMapping("/internal/optimizer/feasibility-check")
    public InfeasibilityDiagnosisResponse feasibility(@RequestHeader("Authorization") String token,
            @RequestParam long profileVersion,
            @RequestBody GenerateDailyMealRequest input) {
        return tools.feasibility(token, profileVersion, input);
    }

    @PutMapping(PLANS + "/{id}/items/{item}/manual-replace")
    public MealPlanResponse replace(@RequestHeader("Authorization") String token,
            @RequestParam long profileVersion,
            @RequestParam long baseVersion,
            @PathVariable long id,
            @PathVariable long item,
            @RequestBody @Valid ManualReplaceItemRequest input) {
        return tools.replace(token, profileVersion, baseVersion, id, item, input);
    }

    @PostMapping(PLANS + "/{id}/swap-dish")
    public MealPlanResponse swap(@RequestHeader("Authorization") String token,
            @RequestParam long profileVersion,
            @RequestParam long baseVersion,
            @PathVariable long id,
            @RequestBody @Valid SwapSingleDishRequest input) {
        return tools.swap(token, profileVersion, baseVersion, id, input);
    }

    @PostMapping(PLANS + "/{id}/rebalance-remaining")
    public MealPlanResponse rebalance(@RequestHeader("Authorization") String token,
            @RequestParam long profileVersion,
            @RequestParam long baseVersion,
            @PathVariable long id,
            @RequestBody @Valid RebalanceRemainingMealsRequest input) {
        return tools.rebalance(token, profileVersion, baseVersion, id, input);
    }

    @GetMapping(PLANS + "/{id}")
    public MealPlanResponse detail(@RequestHeader("Authorization") String token, @PathVariable long id) {
        return optimizer.getMealPlanDetail(accounts.account(token).serviceUserId(), id);
    }

    @PatchMapping(PLANS + "/{id}/items/{item}/status")
    public MealPlanResponse status(@RequestHeader("Authorization") String token, @PathVariable long id,
            @PathVariable long item, @Valid @RequestBody UpdateMealItemStatusRequest input) {
        return optimizer.updateMealItemStatus(accounts.account(token).serviceUserId(), id, item, input.getStatus());
    }

    @PostMapping(PLANS + "/{id}/rate")
    public MealPlanResponse rate(@RequestHeader("Authorization") String token, @PathVariable long id, @Valid @RequestBody RateMealPlanRequest input) {
        return optimizer.rateMealPlan(accounts.account(token).serviceUserId(), id, input);
    }
}
