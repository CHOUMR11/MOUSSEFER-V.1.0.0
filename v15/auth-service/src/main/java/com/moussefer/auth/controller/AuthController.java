package com.moussefer.auth.controller;

import com.moussefer.auth.dto.request.LoginRequest;
import com.moussefer.auth.dto.request.RegisterRequest;
import com.moussefer.auth.dto.response.AuthResponse;
import com.moussefer.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain JWT tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        return ResponseEntity.ok(authService.refreshToken(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate current refresh token")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("X-User-Id") String userId) {
        authService.logout(userId);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/validate")
    @Operation(summary = "Internal endpoint used by gateway to pre-validate tokens")
    public ResponseEntity<Map<String, String>> validate(@RequestHeader("X-User-Id") String userId,
                                                        @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(Map.of("userId", userId, "role", role, "status", "valid"));
    }
}