package com.menufood.userservice.service;

import com.menufood.userservice.dto.request.NutritionProfileRequestDTO;
import com.menufood.userservice.dto.response.NutritionProfileResponseDTO;

public interface NutritionProfileService {
    NutritionProfileResponseDTO profile(String email);

    NutritionProfileResponseDTO saveProfile(String email, NutritionProfileRequestDTO request);
}
