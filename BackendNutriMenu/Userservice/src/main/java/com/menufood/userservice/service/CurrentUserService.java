package com.menufood.userservice.service;

import com.menufood.userservice.entity.User;
import com.menufood.userservice.dao.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final UserRepository users;

    public UUID id(Authentication auth) {
        return users.findByEmail(email(auth)).orElseThrow(() -> new BadCredentialsException("Tài khoản không còn hoạt động")).getId();
    }

    public String email(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            User user = users.findById(UUID.fromString(jwt.getSubject()))
                    .filter(User::isEnabled)
                    .orElseThrow(() -> new BadCredentialsException("Tài khoản không còn hoạt động"));
            return user.getEmail();
        }
        return authentication.getName();
    }
}
