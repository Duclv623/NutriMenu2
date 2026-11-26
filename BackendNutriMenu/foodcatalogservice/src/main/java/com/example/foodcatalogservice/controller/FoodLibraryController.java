package com.example.foodcatalogservice.controller;

import com.example.foodcatalogservice.client.UserIdentityClient;
import com.example.foodcatalogservice.dto.response.DishDetailResponse;
import com.example.foodcatalogservice.entity.enums.PreferenceType;
import com.example.foodcatalogservice.service.FoodCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import com.example.foodcatalogservice.dto.request.*;
import com.example.foodcatalogservice.dto.response.*;
import com.example.foodcatalogservice.entity.enums.MealSlot;
import com.example.foodcatalogservice.service.IngredientService;
import jakarta.validation.Valid;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class FoodLibraryController {
    private final FoodCatalogService catalog;
    private final UserIdentityClient accounts;

    private final IngredientService ingredients;
    public record RecipeIngredient(Long id, String nameVi) {}

    @GetMapping("/foods/tools/ingredients")
    public Map<String, List<RecipeIngredient>> ingredients(@RequestParam(defaultValue = "") String keyword) {
        var page = ingredients.searchIngredients(null, null, keyword, 0, 30);
        return Map.of("content", page.stream().map(i -> new RecipeIngredient(i.getId(), i.getNameVi())).toList());
    }

    @GetMapping("/foods/preferences")
    public Map<String,List<String>> preferences(@RequestHeader("Authorization") String token) {
        var prefs = catalog.getUserPreferences(accounts.identity(token).serviceUserId());
        return Map.of("favorites", prefs.getFavoriteDishes().stream().map(d -> d.getId().toString()).toList(),
                "disliked", prefs.getDislikedDishes().stream().map(d -> d.getId().toString()).toList());
    }

    @PostMapping("/foods/tools/users/me/dishes/{id}/dislike")
    @ResponseStatus(NO_CONTENT)
    public void dislike(@RequestHeader("Authorization") String token, @PathVariable long id) {
        catalog.setUserDishPreference(accounts.identity(token).serviceUserId(), id, PreferenceType.DISLIKE);
    }

    @PostMapping("/foods/tools/users/me/custom-dishes")
    public DishDetailResponse custom(@RequestHeader("Authorization") String token, @Valid @RequestBody CreateDishRequest request) {
        return catalog.createDish(request, accounts.identity(token).serviceUserId());
    }

    @GetMapping("/foods/tools/dishes/{id}/calculate-serving")
    public DynamicServingResponse serving(@RequestHeader("Authorization") String token, @PathVariable long id, @RequestParam BigDecimal multiplier) {
        food(token, id);
        return catalog.calculateDynamicServing(id, multiplier);
    }

    @PostMapping("/foods/tools/dishes/{id}/safety-check")
    public SafetyCheckResponse safety(@RequestHeader("Authorization") String token, @PathVariable long id, @Valid @RequestBody SafetyCheckRequest request) {
        var dish = food(token, id);
        var profile = accounts.profile(token);
        var labels = Map.of("Hải sản", "ALLERGEN_SEAFOOD", "Trứng", "ALLERGEN_EGG",
                "Sữa", "ALLERGEN_DAIRY", "Đậu nành", "ALLERGEN_SOY", "Đậu phộng", "ALLERGEN_PEANUT");
        var tags = java.util.EnumSet.noneOf(com.example.foodcatalogservice.entity.enums.MedicalTag.class);
        if (request.getPatientContraindications() != null) tags.addAll(request.getPatientContraindications());
        boolean unknown = false;
        for (String allergy : profile.allergies()) {
            try { tags.add(com.example.foodcatalogservice.entity.enums.MedicalTag.valueOf(labels.getOrDefault(allergy, allergy))); }
            catch (IllegalArgumentException error) { unknown = true; }
        }
        request.setPatientContraindications(tags);
        var result = catalog.checkPersonalizedSafety(id, request);
        var names = new java.util.ArrayList<String>();
        names.add(dish.getNameVi());
        dish.getRecipeBreakdown().forEach(row -> names.add(row.getIngredientNameVi()));
        boolean excluded = profile.excludedFoods().stream().anyMatch(term -> names.stream().anyMatch(name ->
                normalize(name).contains(normalize(term))));
        if (unknown || excluded || (!profile.excludedFoods().isEmpty() && dish.getRecipeBreakdown().isEmpty())) {
            result.setSafeForPatient(false);
            result.getDetailedWarnings().add(excluded ? "Món có thực phẩm bạn đã loại trừ." : "Thiếu dữ liệu để xác nhận món phù hợp với hồ sơ.");
        }
        return result;
    }

    private static String normalize(String text) {
        return java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').trim();
    }

    @GetMapping("/foods/tools/dishes/{id}/alternatives")
    public List<DishDetailResponse> alternatives(@RequestHeader("Authorization") String token, @PathVariable long id, @RequestParam(required = false) MealSlot mealSlot) {
        food(token, id);
        return catalog.findAlternatives(id, mealSlot);
    }

    @GetMapping("/foods/tools/dishes/compare")
    public DishComparisonResponse compare(@RequestHeader("Authorization") String token, @RequestParam long dish1, @RequestParam long dish2) {
        var user = accounts.identity(token).serviceUserId();
        catalog.getVisibleDish(user, dish1);
        catalog.getVisibleDish(user, dish2);
        return catalog.compareTwoDishes(dish1, dish2);
    }

    @GetMapping("/foods")
    public List<DishDetailResponse> foods(@RequestHeader("Authorization") String token) {
        return catalog.getFoodLibrary(accounts.identity(token).serviceUserId());
    }

    @GetMapping("/foods/{id}")
    public DishDetailResponse food(@RequestHeader("Authorization") String token, @PathVariable long id) {
        return catalog.getVisibleDish(accounts.identity(token).serviceUserId(), id);
    }

    @GetMapping("/me/favorites")
    public List<String> favorites(@RequestHeader("Authorization") String token) {
        return catalog.getUserPreferences(accounts.identity(token).serviceUserId()).getFavoriteDishes().stream()
                .map(dish -> dish.getId().toString()).toList();
    }

    @RequestMapping(path = {"/me/favorites/{id}", "/foods/tools/users/me/dishes/{id}/favorite"}, method = {RequestMethod.PUT, RequestMethod.POST})
    @ResponseStatus(NO_CONTENT)
    public void addFavorite(@RequestHeader("Authorization") String token, @PathVariable long id) {
        catalog.setUserDishPreference(accounts.identity(token).serviceUserId(), id, PreferenceType.FAVORITE);
    }

    @DeleteMapping("/me/favorites/{id}")
    @ResponseStatus(NO_CONTENT)
    public void removeFavorite(@RequestHeader("Authorization") String token, @PathVariable long id) {
        catalog.removeFavorite(accounts.identity(token).serviceUserId(), id);
    }
}
