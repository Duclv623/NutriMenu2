package com.menufood.userservice.controller;

import com.menufood.userservice.dto.request.RegisterRequestDTO;
import com.menufood.userservice.dto.response.CustomResponse;
import com.menufood.userservice.dto.response.UserResponseDTO;
import com.menufood.userservice.service.UserService;
import com.menufood.userservice.service.TokenService;
import com.menufood.userservice.dto.request.LoginRequestDTO;
import com.menufood.userservice.dto.response.LoginResponseDTO;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final TokenService tokenService;
    private final RSAKey signingKey;
    @Value("${app.auth.secure-cookie:false}")
    private boolean secureCookie;

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }

    @PostMapping("/login")
    public CustomResponse<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request, HttpServletResponse response) {
        LoginResponseDTO login = tokenService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("nutriguide_access", login.accessToken())
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/chatbot")
                .maxAge(java.time.Duration.between(java.time.Instant.now(), login.expiresAt())).build().toString());
        return new CustomResponse<>(true, login, "Đăng nhập thành công");
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("nutriguide_access", "")
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path("/api/chatbot").maxAge(0).build().toString());
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomResponse<UserResponseDTO> register(@Valid @RequestBody RegisterRequestDTO request) {
        return new CustomResponse<>(true, userService.register(request), "Đăng ký thành công");
    }
}
