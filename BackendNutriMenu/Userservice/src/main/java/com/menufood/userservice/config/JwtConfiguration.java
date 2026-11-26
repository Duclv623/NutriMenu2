package com.menufood.userservice.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Base64;

@Configuration
public class JwtConfiguration {
    @Bean
    public RSAKey signingKey(@Value("${app.jwt.private-key-path:}") String privatePath,
                             @Value("${app.jwt.public-key-path:}") String publicPath,
                             @Value("${app.environment:development}") String environment) throws Exception {
        RSAPublicKey publicKey;
        RSAPrivateKey privateKey;
        if (!privatePath.isBlank() && !publicPath.isBlank()) {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(readPem(privatePath)));
            publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(readPem(publicPath)));
            if (!privateKey.getModulus().equals(publicKey.getModulus()) || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT requires a matching RSA key pair of at least 2048 bits");
            }
        } else {
            if (!privatePath.isBlank() || !publicPath.isBlank() || !"development".equals(environment)) {
                throw new IllegalStateException("Configure both JWT_PRIVATE_KEY_PATH and JWT_PUBLIC_KEY_PATH");
            }
            // Development only. Tokens are invalidated when User Service restarts.
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            publicKey = (RSAPublicKey) pair.getPublic();
            privateKey = (RSAPrivateKey) pair.getPrivate();
        }
        String kid = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
        return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
    }

    private byte[] readPem(String path) throws Exception {
        return Base64.getDecoder().decode(Files.readString(Path.of(path))
                .replaceAll("-----[^-]+-----", "").replaceAll("\\s", ""));
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey key, @Value("${app.jwt.issuer}") String issuer,
                                 @Value("${app.jwt.audience}") String audience) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<java.time.Instant>("exp", java.util.Objects::nonNull),
                new JwtClaimValidator<java.time.Instant>("iat", java.util.Objects::nonNull),
                new JwtClaimValidator<java.util.List<String>>("aud", aud -> aud != null && aud.contains(audience)),
                new JwtClaimValidator<String>("sub", value -> {
                    try { java.util.UUID.fromString(value); return true; }
                    catch (Exception error) { return false; }
                })));
        return decoder;
    }
}
