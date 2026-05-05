package com.moussefer.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Password reset token.
 *
 * Created when a user calls POST /api/v1/auth/forgot-password.
 * The token is sent to the user's email via Kafka + notification-service.
 * The user clicks the reset link in the email, which takes them to the
 * frontend reset page where they enter a new password and the frontend
 * calls POST /api/v1/auth/reset-password with the token.
 *
 * Security properties:
 *   - Single-use: the token's `used` flag flips to true on successful reset
 *   - Short-lived: default 1 hour expiry
 *   - Bound to one email: the reset call must match the stored email
 *   - Uses a random UUID (122 bits of entropy)
 *
 * To prevent user enumeration attacks, the /forgot-password endpoint
 * ALWAYS returns 200 OK, regardless of whether the email exists.
 */
@Entity
@Table(name = "password_reset_tokens",
       indexes = {
           @Index(name = "idx_prt_token",    columnList = "token", unique = true),
           @Index(name = "idx_prt_user",     columnList = "user_id"),
           @Index(name = "idx_prt_expires",  columnList = "expires_at")
       })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Column(nullable = false, length = 120)
    private String email;

    /** The opaque token sent to the user's email (also used as the URL path param). */
    @Column(nullable = false, length = 100, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Request metadata — handy for forensic in case of abuse. */
    @Column(name = "requested_ip", length = 45)
    private String requestedIp;
}
