package com.moussefer.auth.controller;

import com.moussefer.auth.dto.request.LoginRequest;
import com.moussefer.auth.dto.request.RegisterRequest;
import com.moussefer.auth.dto.response.AuthResponse;
import com.moussefer.auth.service.AuthService;
import com.moussefer.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
    private final PasswordResetService passwordResetService;

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

    /**
     * V23 — Robust logout that survives expired access tokens.
     *
     * The frontend may call /logout when its access token has just expired
     * (e.g. tab left open all night). Previously the gateway rejected the
     * call with 401 and the client never knew its server-side refresh
     * token was still valid. To fix this, /logout is now a public route
     * (no JWT enforcement at the gateway). The userId is resolved from:
     *   1. X-User-Id header if the gateway forwarded one (token still valid), OR
     *   2. the refresh token sent in the body (token expired but refresh known).
     *
     * Idempotent: if the user is already logged out, returns 200 anyway.
     * Returns 400 only if neither identifier is provided.
     */
    @PostMapping("/logout")
    @Operation(summary = "Invalidate current refresh token (works even with expired access token)",
            description = "Provide either X-User-Id header or refreshToken in body. " +
                    "Always returns 200 if logout succeeds, including idempotent re-call.")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "X-User-Id", required = false) String userIdFromHeader,
            @RequestBody(required = false) Map<String, String> body) {
        String userId = userIdFromHeader;
        if ((userId == null || userId.isBlank()) && body != null) {
            String refreshToken = body.get("refreshToken");
            if (refreshToken != null && !refreshToken.isBlank()) {
                userId = authService.resolveUserIdFromRefreshToken(refreshToken);
            }
        }
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "BAD_REQUEST",
                    "message", "Provide X-User-Id header or refreshToken in body"));
        }
        authService.logout(userId);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    // V22 — Password reset by email (scenario: "Mot de passe oublié")

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset link",
            description = "Always returns 200 OK regardless of whether the email exists, " +
                    "to prevent user enumeration. If the email is valid, a reset link is sent via email.")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String email = body.get("email");
        String ip = httpRequest.getRemoteAddr();
        passwordResetService.requestPasswordReset(email, ip);
        return ResponseEntity.ok(Map.of(
                "message", "If this email is registered, a password reset link has been sent."
        ));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Confirm a password reset with token and new password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String newPassword = body.get("newPassword");
        passwordResetService.confirmPasswordReset(token, newPassword);
        return ResponseEntity.ok(Map.of(
                "message", "Password reset successful. Please log in with your new password."
        ));
    }

    @GetMapping("/validate")
    @Operation(summary = "Internal endpoint used by gateway to pre-validate tokens")
    public ResponseEntity<Map<String, String>> validate(@RequestHeader("X-User-Id") String userId,
                                                        @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(Map.of("userId", userId, "role", role, "status", "valid"));
    }

    /**
     * Admin-only endpoint — create a user account manually.
     *
     * Used for creating ORGANIZER accounts which cannot self-register,
     * or other admin-created accounts. Protected by the internal secret
     * header via InternalAuthFilter and further gated in admin-service.
     */
    @PostMapping("/internal/admin/create-user")
    @Operation(summary = "Internal: create a user account (admin-only, used for ORGANIZER)")
    public ResponseEntity<Map<String, Object>> adminCreateUser(
            @RequestHeader(value = "X-Admin-Id", required = false) String adminId,
            @RequestBody Map<String, Object> body) {
        String email = String.valueOf(body.getOrDefault("email", "")).trim();
        String password = String.valueOf(body.getOrDefault("password", ""));
        String phoneNumber = body.get("phoneNumber") != null ? String.valueOf(body.get("phoneNumber")) : null;
        String roleStr = String.valueOf(body.getOrDefault("role", "ORGANIZER")).toUpperCase();

        if (email.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email and password are required"));
        }
        if (password.length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "password must be at least 8 characters"));
        }

        com.moussefer.auth.entity.Role role;
        try {
            role = com.moussefer.auth.entity.Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid role. Expected one of: PASSENGER, DRIVER, ORGANIZER, ADMIN"));
        }

        String userId = authService.createUserAsAdmin(email, password, phoneNumber, role, adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "userId", userId,
                "email", email,
                "role", role.name(),
                "createdBy", adminId != null ? adminId : "unknown",
                "message", role + " account created. User can now login with the provided password."
        ));
    }
}