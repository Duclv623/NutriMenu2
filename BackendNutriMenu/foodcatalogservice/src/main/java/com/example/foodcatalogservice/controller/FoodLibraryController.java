package com.example.foodcatalogservice.controller;

import com.example.foodcatalogservice.client.UserIdentityClient;
import com.example.foodcatalogservice.dto.response.DishDetailResponse;
import com.example.foodcatalogservice.entity.enums.PreferenceType;
import com.example.foodcatalogservice.service.FoodCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class FoodLibraryController {
    private final FoodCatalogService catalog;
    private final UserIdentityClient accounts;

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

    @PutMapping("/me/favorites/{id}")
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
