package com.menufood.userservice.dto.request;

import jakarta.validation.constraints.*;

public record LoginRequestDTO(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(max = 64) String password) {}
