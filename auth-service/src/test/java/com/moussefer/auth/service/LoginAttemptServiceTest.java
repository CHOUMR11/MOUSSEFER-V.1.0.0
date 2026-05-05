package com.moussefer.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour LoginAttemptService.
 *
 * Valide la protection brute-force :
 *   - Compte non bloqué sous le seuil
 *   - Blocage déclenché après MAX_ATTEMPTS échecs
 *   - Remise à zéro après succès
 *   - Temps restant de blocage correct
 */
@DisplayName("LoginAttemptService — protection brute-force")
class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
    }

    @Test
    @DisplayName("Compte non bloqué sans tentatives échouées")
    void notBlocked_initially() {
        assertThat(service.isBlocked("user@test.com")).isFalse();
    }

    @Test
    @DisplayName("Compte non bloqué après 4 tentatives (sous le seuil de 5)")
    void notBlocked_under_threshold() {
        for (int i = 0; i < 4; i++) {
            service.registerFailure("user@test.com");
        }
        assertThat(service.isBlocked("user@test.com")).isFalse();
    }

    @Test
    @DisplayName("Compte bloqué après 5 tentatives échouées")
    void blocked_after_max_attempts() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure("user@test.com");
        }
        assertThat(service.isBlocked("user@test.com")).isTrue();
    }

    @Test
    @DisplayName("Remise à zéro du compteur après une connexion réussie")
    void reset_after_success() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure("user@test.com");
        }
        assertThat(service.isBlocked("user@test.com")).isTrue();

        service.registerSuccess("user@test.com");

        assertThat(service.isBlocked("user@test.com")).isFalse();
        assertThat(service.remainingLockoutSeconds("user@test.com")).isEqualTo(0L);
    }

    @Test
    @DisplayName("Temps restant de blocage supérieur à 0 après verrouillage")
    void remaining_lockout_positive_when_blocked() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure("user@test.com");
        }
        assertThat(service.remainingLockoutSeconds("user@test.com")).isGreaterThan(0L);
    }

    @Test
    @DisplayName("Temps restant est 0 pour un compte non bloqué")
    void remaining_lockout_zero_when_not_blocked() {
        service.registerFailure("user@test.com");
        assertThat(service.remainingLockoutSeconds("user@test.com")).isEqualTo(0L);
    }

    @Test
    @DisplayName("Insensible à la casse de l'email")
    void case_insensitive_email() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure("USER@TEST.COM");
        }
        assertThat(service.isBlocked("user@test.com")).isTrue();
    }

    @Test
    @DisplayName("Isolation entre comptes différents")
    void isolated_per_account() {
        for (int i = 0; i < 5; i++) {
            service.registerFailure("blocked@test.com");
        }
        assertThat(service.isBlocked("other@test.com")).isFalse();
    }

    @Test
    @DisplayName("Pas de NullPointerException avec email null")
    void handles_null_email_gracefully() {
        service.registerFailure(null);
        assertThat(service.isBlocked(null)).isFalse();
    }
}
