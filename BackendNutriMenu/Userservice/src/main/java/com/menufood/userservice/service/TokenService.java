package com.menufood.userservice.service;

import com.menufood.userservice.dto.request.LoginRequestDTO;
import com.menufood.userservice.dto.response.LoginResponseDTO;
import com.menufood.userservice.entity.User;
import com.menufood.userservice.dao.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final UserRepository users;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder encoder;
    @Value("${app.jwt.issuer}") private String issuer;
    @Value("${app.jwt.audience}") private String audience;
    @Value("${app.jwt.ttl-seconds:3600}") private long ttl;

    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = users.findByEmail(request.email().strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BadCredentialsException("Email hoặc mật khẩu không chính xác"));
        if (!user.isEnabled() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không chính xác");
        }
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
                .audience(List.of(audience)).issuedAt(now).expiresAt(expires)
                .claim("roles", List.of(user.getRole())).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new LoginResponseDTO(token, "Bearer", expires, userService.getCurrentUser(user.getEmail()));
    }
}
