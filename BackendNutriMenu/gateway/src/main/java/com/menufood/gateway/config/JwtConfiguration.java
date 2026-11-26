package com.menufood.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Configuration
public class JwtConfiguration {
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${app.jwt.jwk-set-uri}") String jwkSetUri,
                                  @Value("${app.jwt.issuer}") String issuer,
                                  @Value("${app.jwt.audience}") String audience) {
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<Instant>("exp", exp -> exp != null && exp.isAfter(Instant.now())),
                new JwtClaimValidator<Instant>("iat", iat -> iat != null && !iat.isAfter(Instant.now().plusSeconds(60))),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>("sub", JwtConfiguration::isUserId)));
        return decoder;
    }

    private static boolean isUserId(String subject) {
        if (subject == null) return false;
        try {
            return UUID.fromString(subject).toString().equalsIgnoreCase(subject);
        } catch (IllegalArgumentException invalidId) {
            return false;
        }
    }
}
