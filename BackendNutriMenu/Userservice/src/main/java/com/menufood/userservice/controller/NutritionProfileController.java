package com.menufood.userservice.controller;

import com.menufood.userservice.dto.request.NutritionProfileRequestDTO;
import com.menufood.userservice.dto.response.NutritionProfileResponseDTO;
import com.menufood.userservice.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/profile")
public class NutritionProfileController {
    private final CurrentUserService current;
    private final NutritionProfileService profiles;

    @GetMapping
    public NutritionProfileResponseDTO get(Authentication auth) {
        return profiles.profile(current.email(auth));
    }

    @PutMapping
    public NutritionProfileResponseDTO update(Authentication auth, @RequestBody NutritionProfileRequestDTO request) {
        return profiles.saveProfile(current.email(auth), request);
    }
}
