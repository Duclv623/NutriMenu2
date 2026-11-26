package com.menufood.userservice.dto.response;

import java.time.Instant;

public record LoginResponseDTO(String accessToken, String tokenType, Instant expiresAt, UserResponseDTO user) {}
