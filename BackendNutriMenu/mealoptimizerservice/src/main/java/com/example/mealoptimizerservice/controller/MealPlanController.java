package com.example.mealoptimizerservice.controller;

import com.example.mealoptimizerservice.dto.request.*;
import java.time.LocalDate;
import java.util.Set;
import com.example.mealoptimizerservice.client.MealAccountClient;
import com.example.mealoptimizerservice.service.MealOptimizerService;
import com.example.mealoptimizerservice.dto.response.ShoppingListResponse;
import com.example.mealoptimizerservice.dto.response.NutritionalAnalyticsResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import com.example.mealoptimizerservice.dto.response.PersonalMealPlanResponseDTO;
import com.example.mealoptimizerservice.service.PersonalMealPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MealPlanController {
    private final PersonalMealPlanService meals;

    private final MealAccountClient accounts;
    private final MealOptimizerService optimizer;

    @GetMapping("/meal-plans/shopping-list")
    public ShoppingListResponse shopping(@RequestHeader("Authorization") String token,
            @RequestParam LocalDate fromDate, @RequestParam LocalDate toDate) {
        if (toDate.isBefore(fromDate)) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        return optimizer.generateSmartShoppingList(accounts.account(token).serviceUserId(), fromDate, toDate);
    }

    @GetMapping("/meal-plans/analytics")
    public NutritionalAnalyticsResponse analytics(@RequestHeader("Authorization") String token,
            @RequestParam @Min(1) @Max(90) int days) {
        return optimizer.getNutritionalAnalytics(accounts.account(token).serviceUserId(), days);
    }

    @GetMapping("/meal-plans/recent-dishes")
    public Set<Long> recentDishes(@RequestHeader("Authorization") String token, @RequestParam @Min(1) @Max(90) int days) {
        return optimizer.getRecentEatenDishIds(accounts.account(token).serviceUserId(), days);
    }

    @PostMapping("/meal-plans/generate")
    public PersonalMealPlanResponseDTO generate(@RequestHeader("Authorization") String token, @Valid @RequestBody GenerateMealPlanRequestDTO request) {
        return meals.generate(token, request);
    }

    @PostMapping("/meal-plans/generate-weekly")
    public List<PersonalMealPlanResponseDTO> generateWeekly(@RequestHeader("Authorization") String token,
            @Valid @RequestBody GenerateMealPlanRequestDTO request) {
        return meals.generateWeekly(token, request);
    }

    @PostMapping("/meal-plans/{id}/swap-preview")
    public PersonalMealPlanResponseDTO preview(@RequestHeader("Authorization") String token, @PathVariable long id,
                                         @Valid @RequestBody SwapMealRequestDTO request) {
        return meals.change(token, id, request, true);
    }

    @PostMapping("/meal-plans/{id}/swap")
    public PersonalMealPlanResponseDTO apply(@RequestHeader("Authorization") String token, @PathVariable long id,
                                       @Valid @RequestBody SwapMealRequestDTO request) {
        return meals.change(token, id, request, false);
    }

    @PatchMapping("/meal-plans/{id}/items/{item}/lock")
    public PersonalMealPlanResponseDTO lock(@RequestHeader("Authorization") String token, @PathVariable long id, @PathVariable long item,
                                      @Valid @RequestBody LockMealRequestDTO request) {
        return meals.lock(token, id, item, request);
    }

    @DeleteMapping("/meal-plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader("Authorization") String token, @PathVariable long id) {
        meals.delete(token, id);
    }

    @GetMapping("/meal-plans")
    public List<PersonalMealPlanResponseDTO> list(@RequestHeader("Authorization") String token) {
        return meals.list(token);
    }

    @GetMapping("/meal-plans/capabilities")
    public Map<String, Boolean> capabilities() {
        return Map.of("generate", true, "weeklyGeneration", true, "regenerate", true, "swap", true, "favoriteRemoval", true, "atomicVersions", true, "cookingTime", false);
    }

    @PutMapping("/me/meal-logs")
    public PersonalMealPlanResponseDTO complete(@RequestHeader("Authorization") String token, @Valid @RequestBody MealLogRequestDTO request) {
        return meals.complete(token, request);
    }

    @GetMapping("/internal/me/meal-context")
    public JsonNode context(@RequestHeader("Authorization") String token, @RequestParam(required = false) Long planId) {
        return meals.snapshot(token, planId);
    }
}
