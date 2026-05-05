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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour AuthService.
 *
 * Couvre les scénarios critiques :
 *   - Inscription : succès, email dupliqué, téléphone dupliqué
 *   - Connexion : succès, credentials invalides, protection brute-force
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — authentification")
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock JwtService jwtService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock WebClient userServiceWebClient;
    @Mock LoginAttemptService loginAttemptService;

    @InjectMocks AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@moussefer.tn");
        registerRequest.setPassword("Password1");
        registerRequest.setPhoneNumber("+21612345678");
        registerRequest.setRole(Role.PASSENGER);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@moussefer.tn");
        loginRequest.setPassword("Password1");

        savedUser = User.builder()
                .id("user-uuid-001")
                .email("test@moussefer.tn")
                .passwordHash("$2a$12$hashedpassword")
                .phoneNumber("+21612345678")
                .role(Role.PASSENGER)
                .build();
    }

    // ── INSCRIPTION ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Inscription réussie — événement Kafka émis")
    void register_success_emits_kafka_event() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$hashedpassword");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateAccessToken(anyString(), anyString(), any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(anyString(), anyString())).thenReturn("refresh-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);

        AuthResponse response = authService.register(registerRequest);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(kafkaTemplate).send(eq("user.registered"), eq("user-uuid-001"), any(UserRegisteredEvent.class));
        verify(passwordEncoder).encode("Password1");
    }

    @Test
    @DisplayName("Inscription échoue si email déjà utilisé")
    void register_fails_when_email_exists() {
        when(userRepository.existsByEmail("test@moussefer.tn")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Email already registered");

        verify(userRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Inscription échoue si téléphone déjà utilisé")
    void register_fails_when_phone_exists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+21612345678")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Phone number already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Inscription — mot de passe est haché avant persistance")
    void register_hashes_password() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(anyString())).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("$2a$12$HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            assertThat(u.getPasswordHash()).isEqualTo("$2a$12$HASHED");
            assertThat(u.getPasswordHash()).doesNotContain("Password1");
            return savedUser;
        });
        when(jwtService.generateAccessToken(any(), any(), any(), any())).thenReturn("tok");
        when(jwtService.generateRefreshToken(any(), any())).thenReturn("ref");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);
        when(jwtService.getRefreshExpirationMs()).thenReturn(604800000L);

        authService.register(registerRequest);

        verify(passwordEncoder).encode("Password1");
    }

    // ── CONNEXION ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Connexion échoue si compte bloqué par brute-force")
    void login_fails_when_account_blocked() {
        when(loginAttemptService.isBlocked("test@moussefer.tn")).thenReturn(true);
        when(loginAttemptService.remainingLockoutSeconds(anyString())).thenReturn(720L);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Too many failed login attempts");

        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("Connexion échoue si utilisateur non trouvé — registerFailure appelé")
    void login_fails_user_not_found_registers_failure() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail("test@moussefer.tn")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid credentials");

        verify(loginAttemptService).registerFailure("test@moussefer.tn");
    }

    @Test
    @DisplayName("Connexion échoue si mot de passe incorrect — registerFailure appelé")
    void login_fails_wrong_password_registers_failure() {
        when(loginAttemptService.isBlocked(anyString())).thenReturn(false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("Password1", "$2a$12$hashedpassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid credentials");

        verify(loginAttemptService).registerFailure("test@moussefer.tn");
    }
}
