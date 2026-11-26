package com.menufood.userservice.service;

import com.menufood.userservice.dto.request.UserProfileRequestDTO;
import com.menufood.userservice.dto.response.UserProfileResponseDTO;

public interface UserProfileService {
    UserProfileResponseDTO getProfile(String email);

    UserProfileResponseDTO saveProfile(String email, UserProfileRequestDTO request);
}
