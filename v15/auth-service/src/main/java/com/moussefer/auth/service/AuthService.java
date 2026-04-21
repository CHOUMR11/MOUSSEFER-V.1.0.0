package com.moussefer.auth.service;

import com.moussefer.auth.dto.request.LoginRequest;
import com.moussefer.auth.dto.request.RegisterRequest;
import com.moussefer.auth.dto.response.AuthResponse;
import com.moussefer.auth.entity.Role;
import com.moussefer.auth.entity.User;
import com.moussefer.auth.exception.AuthException;
import com.moussefer.auth.kafka.UserRegisteredEvent;
import com.moussefer.auth.repository.UserRepository;
import com.moussefer.auth.security.JwtService;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE_REF =
            new ParameterizedTypeReference<>() {};

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final WebClient userServiceWebClient;      // for user-service (active, suspension)
    private final WebClient adminServiceWebClient;     // ✅ new: for admin-service (admin role)
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email already registered: " + request.getEmail());
        }
        if (request.getPhoneNumber() != null && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new AuthException("Phone number already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(request.getRole())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: id={} role={}", user.getId(), user.getRole());

        kafkaTemplate.send("user.registered", user.getId(),
                new UserRegisteredEvent(user.getId(), user.getEmail(), user.getPhoneNumber(), user.getRole().name()));

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Brute-force protection
        if (loginAttemptService.isBlocked(request.getEmail())) {
            long secs = loginAttemptService.remainingLockoutSeconds(request.getEmail());
            throw new AuthException(
                    "Too many failed login attempts. Try again in " + Math.max(1, secs / 60) + " minute(s)."
            );
        }

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    loginAttemptService.registerFailure(request.getEmail());
                    return new AuthException("Invalid credentials");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAttemptService.registerFailure(request.getEmail());
            throw new AuthException("Invalid credentials");
        }

        // ✅ Bug #7 FIX: Re‑enable active and suspension checks with correct URLs
        if (!checkUserActive(user.getId())) {
            throw new AuthException("Account is deactivated. Contact support.");
        }
        if (checkUserSuspended(user.getId())) {
            throw new AuthException("Account is temporarily suspended.");
        }

        String adminRole = fetchAdminRoleIfAdmin(user);
        userRepository.updateLastLogin(user.getId());
        loginAttemptService.registerSuccess(request.getEmail());
        log.info("User logged in: id={}", user.getId());

        return generateTokensAndResponse(user, adminRole);
    }

    @Retry(name = "userService", fallbackMethod = "fallbackUserActive")
    private boolean checkUserActive(String userId) {
        Boolean active = userServiceWebClient.get()
                .uri("/api/v1/users/internal/{userId}/active", userId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .block(Duration.ofSeconds(3));
        return Boolean.TRUE.equals(active);
    }

    @SuppressWarnings("unused")
    private boolean fallbackUserActive(String userId, Throwable ignored) {
        log.warn("User-service unavailable for active check on userId {}, assuming active", userId);
        return true;
    }

    @Retry(name = "userService", fallbackMethod = "fallbackUserSuspended")
    private boolean checkUserSuspended(String userId) {
        // ✅ CORRECTED URL: points to user-service internal admin endpoint
        Map<String, Object> response = userServiceWebClient.get()
                .uri("/api/v1/users/internal/admin/{userId}/suspension", userId)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block(Duration.ofSeconds(3));
        return response != null && response.get("suspendedUntil") != null;
    }

    @SuppressWarnings("unused")
    private boolean fallbackUserSuspended(String userId, Throwable ignored) {
        log.warn("User-service unavailable for suspension check on userId {}, assuming not suspended", userId);
        return false;
    }

    private String fetchAdminRoleIfAdmin(User user) {
        if (user.getRole() != Role.ADMIN) {
            return null;
        }
        try {
            return fetchAdminRole(user.getId());
        } catch (Exception e) {
            log.warn("Failed to fetch admin role for admin user {}, using null", user.getId(), e);
            return null;
        }
    }

    @Retry(name = "adminService", fallbackMethod = "fallbackAdminRole")
    private String fetchAdminRole(String userId) {
        // ✅ CORRECTED: call admin-service, not user-service
        Map<String, Object> response = adminServiceWebClient.get()
                .uri("/api/v1/admin/internal/users/{userId}/admin-role", userId)
                .retrieve()
                .bodyToMono(MAP_TYPE_REF)
                .block(Duration.ofSeconds(3));
        if (response != null && response.get("adminRole") != null) {
            return (String) response.get("adminRole");
        }
        return null;
    }

    @SuppressWarnings("unused")
    private String fallbackAdminRole(String userId, Throwable ignored) {
        log.warn("Admin-service unavailable for admin role fetch, assuming non-admin for {}", userId);
        return null;
    }

    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtService.isTokenValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new AuthException("Invalid refresh token");
        }

        String userId = Objects.toString(jwtService.extractAllClaims(refreshToken).get("userId"), null);
        if (userId == null || userId.isBlank()) {
            throw new AuthException("Invalid refresh token");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Invalid refresh token"));

        if (user.getRefreshTokenExpiry() == null || user.getRefreshTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new AuthException("Refresh token expired. Please log in again.");
        }

        if (user.getRefreshToken() == null || !passwordEncoder.matches(refreshToken, user.getRefreshToken())) {
            throw new AuthException("Refresh token is invalid");
        }

        String adminRole = fetchAdminRoleIfAdmin(user);
        return generateTokensAndResponse(user, adminRole);
    }

    @Transactional
    public void logout(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            userRepository.updateRefreshToken(userId, null, null);
            log.info("User logged out: id={}", userId);
        });
    }

    private AuthResponse buildAuthResponse(User user) {
        String adminRole = fetchAdminRoleIfAdmin(user);
        return generateTokensAndResponse(user, adminRole);
    }

    private AuthResponse generateTokensAndResponse(User user, String adminRole) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), adminRole);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());

        LocalDateTime refreshExpiry = LocalDateTime.now()
                .plusSeconds(jwtService.getRefreshExpirationMs() / 1000);
        userRepository.updateRefreshToken(user.getId(), passwordEncoder.encode(refreshToken), refreshExpiry);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getExpirationSeconds())
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .adminRole(adminRole)
                .build();
    }
}