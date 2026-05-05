package com.moussefer.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String secret = "test-jwt-secret-that-is-256-bits-long-enough";
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(secret);
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldValidateValidToken() {
        String token = Jwts.builder()
                .subject("user@example.com")
                .claim("userId", "123")
                .claim("role", "PASSAGER")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = Jwts.builder()
                .subject("user@example.com")
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(key)
                .compact();
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void shouldExtractSubject() {
        String token = Jwts.builder()
                .subject("user@example.com")
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();
        assertThat(jwtService.extractSubject(token)).isEqualTo("user@example.com");
    }
}