package com.moussefer.auth.service;

import com.moussefer.auth.entity.PasswordResetToken;
import com.moussefer.auth.entity.User;
import com.moussefer.auth.exception.AuthException;
import com.moussefer.auth.repository.PasswordResetTokenRepository;
import com.moussefer.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Password reset by email workflow (V22 — implements scenario "Mot de passe oublié").
 *
 * Flow:
 *   1. User calls POST /forgot-password with email
 *   2. We look up the user (but never reveal whether they exist — anti-enumeration)
 *   3. If found, generate a random 43-char URL-safe token + 1h expiry
 *   4. Invalidate any prior pending tokens for this user
 *   5. Publish Kafka event `auth.password_reset_requested` so notification-service
 *      sends an email with the reset link:
 *          https://moussefer.tn/reset-password?token={token}
 *   6. Return 200 OK always (same response for unknown email)
 *
 *   7. User clicks email link → frontend reset page
 *   8. User types new password + submits → POST /reset-password {token, newPassword}
 *   9. We validate the token (exists, not used, not expired)
 *  10. Update user's passwordHash, mark token used
 *  11. Optionally invalidate all refresh tokens for security
 *
 * Security:
 *   - Tokens use SecureRandom + Base64 URL-safe encoding (256 bits of entropy)
 *   - Single-use (flagged `used = true` on consumption)
 *   - Single-active (requesting a new token invalidates older ones)
 *   - Fresh password must meet the same rules as registration
 *   - Emails that don't exist → identical response (no timing side-channel should be
 *     perfect, but we do a fake hash pass to equalize response time)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${password-reset.token-ttl-minutes:60}")
    private long tokenTtlMinutes;

    @Value("${password-reset.frontend-base-url:https://moussefer.tn}")
    private String frontendBaseUrl;

    /**
     * Step 1 — forgot password request.
     *
     * Always succeeds (200 OK) regardless of whether the email exists.
     * The Kafka event is only published if the user actually exists.
     */
    @Transactional
    public void requestPasswordReset(String email, String requestIp) {
        if (email == null || email.isBlank()) {
            throw new AuthException("Email is required");
        }
        String normalizedEmail = email.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isEmpty()) {
            // Fake work to equalize timing — don't give away whether the email exists
            passwordEncoder.encode("decoy-password-to-equalize-timing");
            log.info("Password reset requested for non-existent email (ignored): {}", normalizedEmail);
            return;
        }

        User user = userOpt.get();

        // Invalidate any pending tokens for this user
        tokenRepository.invalidateAllForUser(user.getId(), LocalDateTime.now());

        // Generate a fresh token
        String tokenValue = generateSecureToken();
        PasswordResetToken token = PasswordResetToken.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .token(tokenValue)
                .expiresAt(LocalDateTime.now().plusMinutes(tokenTtlMinutes))
                .used(false)
                .requestedIp(requestIp)
                .build();
        tokenRepository.save(token);

        // Publish Kafka event — notification-service consumes it and sends the email
        Map<String, Object> event = new HashMap<>();
        event.put("userId", user.getId());
        event.put("email", user.getEmail());
        event.put("resetToken", tokenValue);
        event.put("resetUrl", frontendBaseUrl + "/reset-password?token=" + tokenValue);
        event.put("expiresInMinutes", tokenTtlMinutes);
        event.put("requestedAt", LocalDateTime.now().toString());
        kafkaTemplate.send("auth.password_reset_requested", user.getId(), event);

        log.info("Password reset token issued for userId={} (email={}), valid {} minutes",
                user.getId(), user.getEmail(), tokenTtlMinutes);
    }

    /**
     * Step 2 — confirm reset with token + new password.
     */
    @Transactional
    public void confirmPasswordReset(String tokenValue, String newPassword) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new AuthException("Reset token is required");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new AuthException("Password must be at least 8 characters");
        }
        // Same rule as registration — one upper + one lower + one digit
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$")) {
            throw new AuthException(
                    "Password must contain at least one uppercase letter, one lowercase letter, and one digit");
        }

        PasswordResetToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new AuthException("Invalid or unknown reset token"));

        if (token.isUsed()) {
            throw new AuthException("This reset token has already been used");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AuthException("This reset token has expired — request a new one");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new AuthException("User no longer exists"));

        // Apply new password and invalidate all refresh tokens for safety
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setRefreshToken(null);
        user.setRefreshTokenExpiry(null);
        userRepository.save(user);

        token.setUsed(true);
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);

        // Publish confirmation event — can be consumed by notification-service
        // to send "your password was changed" confirmation email.
        Map<String, Object> event = new HashMap<>();
        event.put("userId", user.getId());
        event.put("email", user.getEmail());
        event.put("resetAt", LocalDateTime.now().toString());
        kafkaTemplate.send("auth.password_reset_completed", user.getId(), event);

        log.info("Password successfully reset for userId={} using token id={}",
                user.getId(), token.getId());
    }

    /**
     * Generates a 43-character URL-safe token (32 random bytes → Base64 without padding).
     * ~256 bits of entropy.
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
