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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
    private final WebClient userServiceWebClient;
    private final LoginAttemptService loginAttemptService;

    // ========================= REGISTER =========================

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // SECURITY: only PASSENGER and DRIVER can self-register.
        if (!request.isRoleSelfRegistrable()) {
            log.warn("Self-registration blocked for role {}: {}", request.getRole(), request.getEmail());
            throw new AuthException(
                    "Self-registration is only allowed for PASSENGER and DRIVER roles. " +
                            "ORGANIZER accounts must be created by a platform administrator.");
        }
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

    // ========================= ADMIN CREATE USER =========================

    @Transactional
    public String createUserAsAdmin(String email, String rawPassword, String phoneNumber, Role role, String createdByAdminId) {
        if (userRepository.existsByEmail(email)) {
            throw new AuthException("Email already registered: " + email);
        }
        if (phoneNumber != null && !phoneNumber.isBlank() && userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new AuthException("Phone number already registered");
        }
        if (role == null) {
            throw new AuthException("Role is required when creating a user as admin");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .phoneNumber(phoneNumber)
                .role(role)
                .build();
        user = userRepository.save(user);

        log.info("Admin {} created new {} account: userId={}, email={}",
                createdByAdminId, role, user.getId(), email);

        kafkaTemplate.send("user.registered", user.getId(),
                new UserRegisteredEvent(user.getId(), user.getEmail(),
                        user.getPhoneNumber(), user.getRole().name()));

        return user.getId();
    }

    // ========================= LOGIN =========================

    @Transactional
    public AuthResponse login(LoginRequest request) {
        if (loginAttemptService.isBlocked(request.getEmail())) {
            long secs = loginAttemptService.remainingLockoutSeconds(request.getEmail());
            throw new AuthException("Too many failed login attempts. Try again in " + Math.max(1, secs / 60) + " minute(s).");
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

    // ========================= REFRESH TOKEN =========================

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

        // === Vérifications d'activité et suspension (ajoutées) ===
        if (!checkUserActive(user.getId())) {
            throw new AuthException("Account is deactivated. Contact support.");
        }
        if (checkUserSuspended(user.getId())) {
            throw new AuthException("Account is temporarily suspended.");
        }

        if (user.getRefreshTokenExpiry() == null || user.getRefreshTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new AuthException("Refresh token expired. Please log in again.");
        }
        if (user.getRefreshToken() == null || !passwordEncoder.matches(refreshToken, user.getRefreshToken())) {
            throw new AuthException("Refresh token is invalid");
        }
        String adminRole = fetchAdminRoleIfAdmin(user);
        return generateTokensAndResponse(user, adminRole);
    }

    // ========================= LOGOUT =========================

    @Transactional
    public void logout(String userId) {
        userRepository.findById(userId).ifPresent(user -> {
            userRepository.updateRefreshToken(userId, null, null);
            log.info("User logged out: id={}", userId);
        });
    }

    // ========================= HELPERS / RESOLVE USERID =========================

    public String resolveUserIdFromRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return null;
        try {
            if (!jwtService.isTokenValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
                return null;
            }
            return Objects.toString(jwtService.extractAllClaims(refreshToken).get("userId"), null);
        } catch (Exception e) {
            log.debug("Could not resolve userId from refresh token: {}", e.getMessage());
            return null;
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String adminRole = fetchAdminRoleIfAdmin(user);
        return generateTokensAndResponse(user, adminRole);
    }

    private AuthResponse generateTokensAndResponse(User user, String adminRole) {
        String accessToken  = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), adminRole);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getEmail());
        LocalDateTime refreshExpiry = LocalDateTime.now().plusSeconds(jwtService.getRefreshExpirationMs() / 1000);
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

    // ========================= USER-SERVICE CALLS (with Resilience4j) =========================

    @Retry(name = "userService", fallbackMethod = "fallbackCheckUserActive")
    @CircuitBreaker(name = "userService")
    private boolean checkUserActive(String userId) {
        try {
            Boolean active = userServiceWebClient.get()
                    .uri("/api/v1/users/internal/{userId}/active", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block(Duration.ofSeconds(3));
            return Boolean.TRUE.equals(active);
        } catch (WebClientResponseException.NotFound e) {
            // Profile not yet propagated from Kafka — allow login
            return true;
        } catch (Exception e) {
            log.warn("Failed to check active status for user {}, assuming active (fail-open)", userId);
            return true;
        }
    }

    private boolean fallbackCheckUserActive(String userId, Throwable t) {
        log.warn("Fallback active check for user {} due to {}", userId, t.getMessage());
        return true; // fail-open
    }

    @Retry(name = "userService", fallbackMethod = "fallbackCheckUserSuspended")
    @CircuitBreaker(name = "userService")
    private boolean checkUserSuspended(String userId) {
        try {
            Map<String, Object> profile = userServiceWebClient.get()
                    .uri("/api/v1/users/internal/admin/{userId}", userId)
                    .retrieve()
                    .bodyToMono(MAP_TYPE_REF)
                    .block(Duration.ofSeconds(3));
            if (profile == null) return false;
            Object suspendedUntil = profile.get("suspendedUntil");
            if (suspendedUntil == null) return false;
            try {
                LocalDateTime until = LocalDateTime.parse(suspendedUntil.toString());
                return LocalDateTime.now().isBefore(until);
            } catch (Exception e) {
                return false;
            }
        } catch (WebClientResponseException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("Failed to check suspension for user {}, assuming not suspended (fail-open)", userId);
            return false;
        }
    }

    private boolean fallbackCheckUserSuspended(String userId, Throwable t) {
        log.warn("Fallback suspension check for user {} due to {}", userId, t.getMessage());
        return false; // fail-open: assume not suspended
    }

    private String fetchAdminRoleIfAdmin(User user) {
        if (user.getRole() != Role.ADMIN) return null;
        try {
            return fetchAdminRole(user.getId());
        } catch (Exception e) {
            log.warn("Failed to fetch admin role for user {}: {}", user.getId(), e.getMessage());
            return null;
        }
    }

    @Retry(name = "userService", fallbackMethod = "fallbackFetchAdminRole")
    @CircuitBreaker(name = "userService")
    private String fetchAdminRole(String userId) {
        try {
            Map<String, Object> response = userServiceWebClient.get()
                    .uri("/api/v1/users/internal/admin/{userId}/admin-role", userId)
                    .retrieve()
                    .bodyToMono(MAP_TYPE_REF)
                    .block(Duration.ofSeconds(3));
            if (response != null && response.get("adminRole") != null) {
                String role = (String) response.get("adminRole");
                return "NONE".equals(role) ? null : role;
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to fetch admin role for user {}, defaulting to null (fail-open)", userId);
            return null;
        }
    }

    private String fallbackFetchAdminRole(String userId, Throwable t) {
        log.warn("Fallback admin role fetch for user {} due to {}", userId, t.getMessage());
        return null; // fail-open: assume no admin role
    }
}