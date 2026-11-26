package com.menufood.userservice.service;

import com.menufood.userservice.dto.request.RegisterRequestDTO;
import com.menufood.userservice.dto.response.UserResponseDTO;

public interface UserService {
    UserResponseDTO register(RegisterRequestDTO request);

    UserResponseDTO getCurrentUser(String email);
}
