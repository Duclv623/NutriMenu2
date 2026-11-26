package com.menufood.userservice.service.impl;

import com.menufood.userservice.service.UserProfileService;

import com.menufood.userservice.dto.request.UserProfileRequestDTO;
import com.menufood.userservice.dto.response.UserProfileResponseDTO;
import com.menufood.userservice.entity.User;
import com.menufood.userservice.mapper.UserProfileMapper;
import com.menufood.userservice.entity.UserProfile;
import com.menufood.userservice.exception.ResourceNotFoundException;
import com.menufood.userservice.exception.ProfileVersionConflictException;
import com.menufood.userservice.dao.repository.UserProfileRepository;
import com.menufood.userservice.dao.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileMapper profileMapper;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    @Override
    public UserProfileResponseDTO getProfile(String email) {
        User user = findUser(email);
        UserProfile profile = userProfileRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa tạo hồ sơ dinh dưỡng"));
        return profileMapper.toResponse(profile);
    }

    @Transactional
    @Override
    public UserProfileResponseDTO saveProfile(String email, UserProfileRequestDTO request) {
        User user = findUser(email);
        UserProfile profile = userProfileRepository.findById(user.getId()).orElseGet(() -> {
            UserProfile newProfile = new UserProfile();
            newProfile.setUser(user);
            return newProfile;
        });

        if (profile.getVersion() != null && !profile.getVersion().equals(request.getExpectedVersion())) {
            throw new ProfileVersionConflictException();
        }

        profileMapper.update(profile, request);
        // Cập nhật thời điểm cả khi chỉ thay đổi các danh sách ăn uống.
        profile.setUpdatedAt(Instant.now());

        return profileMapper.toResponse(userProfileRepository.saveAndFlush(profile));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản"));
    }

}
