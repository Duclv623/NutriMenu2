package com.menufood.userservice.controller;

import com.menufood.userservice.dto.request.*;
import com.menufood.userservice.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import java.util.UUID;
import static org.springframework.http.HttpStatus.NO_CONTENT;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me")
public class UserActivityController {
    private final UserActivityService activity;
    private final CurrentUserService current;

    @GetMapping("/shopping-list")
    public JsonNode shopping(Authentication auth) {
        return activity.shoppingList(current.id(auth));
    }

    @PutMapping("/shopping-list")
    @ResponseStatus(NO_CONTENT)
    public void updateShopping(Authentication auth, @Valid @RequestBody ShoppingListRequestDTO request) {
        activity.shopping(current.id(auth), request.checkedIds());
    }

    @GetMapping("/check-ins")
    public JsonNode checkIns(Authentication auth) {
        return activity.checkIns(current.id(auth));
    }

    @PostMapping("/check-ins")
    public JsonNode saveCheckIn(Authentication auth, @Valid @RequestBody CheckInRequestDTO request) {
        return activity.checkIn(current.id(auth), request);
    }

    @DeleteMapping("/check-ins/{id}")
    @ResponseStatus(NO_CONTENT)
    public void deleteCheckIn(Authentication auth, @PathVariable UUID id) {
        activity.deleteCheckIn(current.id(auth), id);
    }
}
