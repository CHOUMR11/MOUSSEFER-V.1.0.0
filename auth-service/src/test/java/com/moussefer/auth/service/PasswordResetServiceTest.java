package com.moussefer.auth.service;

import com.moussefer.auth.entity.PasswordResetToken;
import com.moussefer.auth.entity.User;
import com.moussefer.auth.entity.Role;
import com.moussefer.auth.exception.AuthException;
import com.moussefer.auth.repository.PasswordResetTokenRepository;
import com.moussefer.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests critiques pour le flow de réinitialisation de mot de passe (V22).
 *
 * Couvre les 5 garanties de sécurité du flow :
 *   1. Anti-enumeration — réponse identique si l'email existe ou pas
 *   2. Token cryptographiquement fort — 256 bits d'entropie via SecureRandom
 *   3. Usage unique — flag `used` flippe à true à la consommation
 *   4. TTL court (60 min) — token expiré rejeté
 *   5. Invalidation des sessions actives — refresh tokens wipeés au reset
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService — Tests de sécurité du reset mot de passe (V22)")
class PasswordResetServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(passwordResetService, "tokenTtlMinutes", 60L);
        ReflectionTestUtils.setField(passwordResetService, "frontendBaseUrl", "https://moussefer.tn");
    }

    @Test
    @DisplayName("Anti-enumeration : email inconnu → réponse identique, AUCUN event Kafka publié")
    void requestPasswordReset_unknownEmail_returnsSilently() {
        when(userRepository.findByEmail("inconnu@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("inconnu@example.com", "127.0.0.1");

        verify(tokenRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        // Mais on doit faire le faux bcrypt pour égaliser le timing (anti-side-channel)
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    @DisplayName("Email valide → token sauvegardé + event Kafka avec resetUrl")
    void requestPasswordReset_validEmail_publishesKafkaEvent() {
        User user = User.builder()
                .id("usr-123").email("alice@example.com")
                .passwordHash("hash").role(Role.PASSENGER).build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.invalidateAllForUser(eq("usr-123"), any())).thenReturn(0);

        passwordResetService.requestPasswordReset("alice@example.com", "10.0.0.1");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken saved = tokenCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getUserId()).isEqualTo("usr-123");
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getRequestedIp()).isEqualTo("10.0.0.1");
        assertThat(saved.getExpiresAt())
                .isAfter(LocalDateTime.now().plusMinutes(59))
                .isBefore(LocalDateTime.now().plusMinutes(61));

        verify(kafkaTemplate).send(eq("auth.password_reset_requested"), eq("usr-123"), any());
    }

    @Test
    @DisplayName("Token cryptographiquement fort : 43 caractères URL-safe (256 bits Base64)")
    void requestPasswordReset_generatesStrongToken() {
        User user = User.builder().id("usr-1").email("a@b.c").role(Role.PASSENGER).build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset("a@b.c", "127.0.0.1");

        ArgumentCaptor<PasswordResetToken> cap = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(cap.capture());
        String token = cap.getValue().getToken();
        assertThat(token).hasSize(43);
        assertThat(token).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("Anti-replay : nouveau forgot-password invalide les anciens tokens du même user")
    void requestPasswordReset_invalidatesPreviousTokens() {
        User user = User.builder().id("usr-1").email("a@b.c").role(Role.PASSENGER).build();
        when(userRepository.findByEmail("a@b.c")).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset("a@b.c", "127.0.0.1");

        verify(tokenRepository).invalidateAllForUser(eq("usr-1"), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Token valide + mot de passe conforme → reset OK + sessions invalidées")
    void confirmPasswordReset_validToken_resetsPasswordAndWipesSessions() {
        PasswordResetToken token = PasswordResetToken.builder()
                .id("tok-1").userId("usr-123").token("valid-token-43-chars")
                .email("a@b.c").used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        User user = User.builder()
                .id("usr-123").email("a@b.c")
                .passwordHash("oldHash")
                .refreshToken("active-refresh")
                .refreshTokenExpiry(LocalDateTime.now().plusDays(7))
                .role(Role.PASSENGER).build();
        when(tokenRepository.findByToken("valid-token-43-chars")).thenReturn(Optional.of(token));
        when(userRepository.findById("usr-123")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("newBcryptHash");

        passwordResetService.confirmPasswordReset("valid-token-43-chars", "NewPass1!");

        ArgumentCaptor<User> userCap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCap.capture());
        User updatedUser = userCap.getValue();
        assertThat(updatedUser.getPasswordHash()).isEqualTo("newBcryptHash");
        // CRUCIAL : refresh tokens invalidés (re-login partout)
        assertThat(updatedUser.getRefreshToken()).isNull();
        assertThat(updatedUser.getRefreshTokenExpiry()).isNull();

        ArgumentCaptor<PasswordResetToken> tokenCap = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCap.capture());
        assertThat(tokenCap.getValue().isUsed()).isTrue();
        assertThat(tokenCap.getValue().getUsedAt()).isNotNull();

        verify(kafkaTemplate).send(eq("auth.password_reset_completed"), eq("usr-123"), any());
    }

    @Test
    @DisplayName("Token déjà utilisé → AuthException (anti-replay)")
    void confirmPasswordReset_usedToken_rejected() {
        PasswordResetToken used = PasswordResetToken.builder()
                .id("tok-1").userId("usr-123").token("already-used-token")
                .email("a@b.c").used(true).usedAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build();
        when(tokenRepository.findByToken("already-used-token")).thenReturn(Optional.of(used));

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset("already-used-token", "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("already been used");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Token expiré → AuthException")
    void confirmPasswordReset_expiredToken_rejected() {
        PasswordResetToken expired = PasswordResetToken.builder()
                .id("tok-1").userId("usr-123").token("expired-token")
                .email("a@b.c").used(false)
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .build();
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset("expired-token", "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("expired");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Token inexistant → AuthException")
    void confirmPasswordReset_unknownToken_rejected() {
        when(tokenRepository.findByToken("fake-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset("fake-token", "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Invalid or unknown");
    }

    @Test
    @DisplayName("Mot de passe trop court (< 8 caractères) → rejeté")
    void confirmPasswordReset_passwordTooShort_rejected() {
        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset("any-token", "Short1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    @DisplayName("Mot de passe sans majuscule → rejeté")
    void confirmPasswordReset_passwordNoUppercase_rejected() {
        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset("any-token", "alllower1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    @DisplayName("Mot de passe sans chiffre → rejeté")
    void confirmPasswordReset_passwordNoDigit_rejected() {
        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset("any-token", "NoDigitsHere"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("digit");
    }

    @Test
    @DisplayName("Token null ou vide → rejeté immédiatement")
    void confirmPasswordReset_nullToken_rejected() {
        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset(null, "NewPass1!"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("required");

        assertThatThrownBy(() ->
                passwordResetService.confirmPasswordReset("", "NewPass1!"))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("Email vide → AuthException (input validation)")
    void requestPasswordReset_emptyEmail_rejected() {
        assertThatThrownBy(() ->
                passwordResetService.requestPasswordReset("", "127.0.0.1"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("required");
    }
}
