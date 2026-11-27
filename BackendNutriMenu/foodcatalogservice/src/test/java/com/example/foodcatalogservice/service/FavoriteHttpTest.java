package com.example.foodcatalogservice.service;

import com.example.foodcatalogservice.controller.UserCatalogController;
import com.example.foodcatalogservice.controller.FoodLibraryController;
import com.example.foodcatalogservice.client.UserIdentityClient;
import com.example.foodcatalogservice.entity.Dish;
import com.example.foodcatalogservice.entity.UserFoodPreference;
import com.example.foodcatalogservice.entity.enums.PreferenceType;
import com.example.foodcatalogservice.dao.repository.DishRepository;
import com.example.foodcatalogservice.dao.repository.UserFoodPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class FavoriteHttpTest {
    final Map<String, UserFoodPreference> stored = new HashMap<>();
    MockMvc mvc;
    @BeforeEach void setup() {
        var dishes = mock(DishRepository.class);
        var preferences = mock(UserFoodPreferenceRepository.class);
        when(dishes.findById(4L)).thenReturn(Optional.of(Dish.builder().id(4L).build()));
        when(preferences.findByUserIdAndDishId(anyLong(), anyLong())).thenAnswer(c ->
                Optional.ofNullable(stored.get(c.getArgument(0) + ":" + c.getArgument(1))));
        when(preferences.save(any())).thenAnswer(c -> {
            UserFoodPreference pref = c.getArgument(0);
            stored.put(pref.getUserId() + ":" + pref.getDish().getId(), pref);
            return pref;
        });
        doAnswer(c -> { UserFoodPreference pref = c.getArgument(0); stored.remove(pref.getUserId() + ":" + pref.getDish().getId()); return null; }).when(preferences).delete(any());
        var service = new FoodCatalogService(dishes, null, null, null, preferences, null);
        var accounts = mock(UserIdentityClient.class);
        when(accounts.identity("Bearer test")).thenReturn(new UserIdentityClient.Identity(12L));
        mvc = MockMvcBuilders.standaloneSetup(new UserCatalogController(service),
                new FoodLibraryController(service, accounts, mock(IngredientService.class))).build();
    }
    @Test void removingFavouriteIsIdempotentAndCanBeAddedAgain() throws Exception {
        assertEquals(200, mvc.perform(post("/api/v1/users/me/dishes/4/favorite").header("X-User-Id", 12)).andReturn().getResponse().getStatus());
        assertEquals(PreferenceType.FAVORITE, stored.get("12:4").getPreferenceType());
        for (int i = 0; i < 2; i++) assertEquals(204, mvc.perform(delete("/api/v1/users/me/dishes/4/favorite").header("X-User-Id", 12)).andReturn().getResponse().getStatus());
        assertFalse(stored.containsKey("12:4"));
        mvc.perform(post("/api/v1/users/me/dishes/4/favorite").header("X-User-Id", 12));
        assertTrue(stored.containsKey("12:4"));
    }
    @Test void removingFavouriteDoesNotChangeAnotherUser() throws Exception {
        for (long user : List.of(12L, 13L)) mvc.perform(post("/api/v1/users/me/dishes/4/favorite").header("X-User-Id", user));
        mvc.perform(delete("/api/v1/users/me/dishes/4/favorite").header("X-User-Id", 12));
        assertFalse(stored.containsKey("12:4"));
        assertTrue(stored.containsKey("13:4"));
    }
    @Test void removingFavouritePreservesDislike() throws Exception {
        mvc.perform(post("/api/v1/users/me/dishes/4/dislike").header("X-User-Id", 12));
        mvc.perform(delete("/api/v1/users/me/dishes/4/favorite").header("X-User-Id", 12));
        assertEquals(PreferenceType.DISLIKE, stored.get("12:4").getPreferenceType());
    }
    @Test void missingUserHeaderIsRejected() throws Exception {
        assertEquals(400, mvc.perform(delete("/api/v1/users/me/dishes/4/favorite")).andReturn().getResponse().getStatus());
    }
    @Test void removingDislikeThroughJwtLibraryIsIdempotentAndPreservesAnotherUsersPreference() throws Exception {
        for (long user : List.of(12L,13L)) mvc.perform(post("/api/v1/users/me/dishes/4/dislike").header("X-User-Id",user));
        for (int i=0; i<2; i++) assertEquals(204,mvc.perform(delete("/api/foods/tools/users/me/dishes/4/dislike")
                .header("Authorization","Bearer test")).andReturn().getResponse().getStatus());
        assertFalse(stored.containsKey("12:4"));
        assertEquals(PreferenceType.DISLIKE,stored.get("13:4").getPreferenceType());
        mvc.perform(post("/api/v1/users/me/dishes/4/favorite").header("X-User-Id",12L));
        mvc.perform(delete("/api/foods/tools/users/me/dishes/4/dislike").header("Authorization","Bearer test"));
        assertEquals(PreferenceType.FAVORITE,stored.get("12:4").getPreferenceType());
    }
}
