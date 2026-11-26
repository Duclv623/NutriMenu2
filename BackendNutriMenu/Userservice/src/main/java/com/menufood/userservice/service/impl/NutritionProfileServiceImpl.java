package com.menufood.userservice.service.impl;

import com.menufood.userservice.service.NutritionProfileService;
import com.menufood.userservice.service.UserProfileService;
import com.menufood.userservice.service.UserService;

import com.menufood.userservice.dto.request.NutritionProfileRequestDTO;
import com.menufood.userservice.dto.response.NutritionProfileResponseDTO;
import com.menufood.userservice.mapper.NutritionProfileMapper;
import com.menufood.userservice.dao.repository.UserProfileRepository;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@RequiredArgsConstructor
@Transactional
public class NutritionProfileServiceImpl implements NutritionProfileService {
    private final UserService users;
    private final UserProfileService profiles;
    private final UserProfileRepository profileRepository;
    private final NutritionProfileMapper profileMapper;
    private final Validator validator;

    @Override
    public NutritionProfileResponseDTO profile(String email) {
        var user = users.getCurrentUser(email);
        return profileMapper.toResponse(user.getFullName(), profileRepository.existsById(user.getId()) ? profiles.getProfile(email) : null);
    }

    @Override
    public NutritionProfileResponseDTO saveProfile(String email, NutritionProfileRequestDTO request) {
        var user = users.getCurrentUser(email);
        var dto = profileMapper.toUserProfile(request);
        var errors = validator.validate(dto);
        if (!errors.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, errors.iterator().next().getMessage());
        return profileMapper.toResponse(user.getFullName(), profiles.saveProfile(email, dto));
    }
}
