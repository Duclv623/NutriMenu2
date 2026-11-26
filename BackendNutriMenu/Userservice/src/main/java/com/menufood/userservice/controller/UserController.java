package com.menufood.userservice.controller;

import com.menufood.userservice.dto.request.UserProfileRequestDTO;
import com.menufood.userservice.dto.response.CustomResponse;
import com.menufood.userservice.dto.response.UserProfileResponseDTO;
import com.menufood.userservice.dto.response.UserResponseDTO;
import com.menufood.userservice.service.UserProfileService;
import com.menufood.userservice.service.UserService;
import com.menufood.userservice.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserProfileService userProfileService;
    private final CurrentUserService currentUser;

    @GetMapping
    public CustomResponse<UserResponseDTO> getCurrentUser(Authentication authentication) {
        return new CustomResponse<>(true, userService.getCurrentUser(currentUser.email(authentication)),
                "Lấy thông tin tài khoản thành công");
    }

    @GetMapping("/profile")
    public CustomResponse<UserProfileResponseDTO> getProfile(Authentication authentication) {
        return new CustomResponse<>(true, userProfileService.getProfile(currentUser.email(authentication)),
                "Lấy hồ sơ dinh dưỡng thành công");
    }

    @PutMapping("/profile")
    public CustomResponse<UserProfileResponseDTO> saveProfile(Authentication authentication,
                                                             @Valid @RequestBody UserProfileRequestDTO request) {
        return new CustomResponse<>(true, userProfileService.saveProfile(currentUser.email(authentication), request),
                "Lưu hồ sơ dinh dưỡng thành công");
    }
}
