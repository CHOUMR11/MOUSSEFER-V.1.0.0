package com.moussefer.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protège le endpoint /auth/login contre les attaques bruteforce.
 *
 * Stratégie :
 *   - Max 5 tentatives échouées par email dans une fenêtre de 15 minutes.
 *   - Au-delà : compte bloqué 15 minutes, toute tentative réinitialise le timer.
 *   - Succès de connexion : compteur remis à zéro.
 *
 * Stockage en mémoire (ConcurrentHashMap) — suffisant pour un déploiement
 * mono-instance. Pour multi-instance, remplacer par Redis avec TTL.
 */
@Service
@Slf4j
public class LoginAttemptService {

    private static final int    MAX_ATTEMPTS        = 5;
    private static final int    LOCKOUT_MINUTES     = 15;
    private static final int    WINDOW_MINUTES      = 15;

    private record AttemptRecord(int count, LocalDateTime windowStart, LocalDateTime lockedUntil) {}

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    /**
     * Vérifie si le compte est actuellement bloqué.
     *
     * @param email identifiant du compte
     * @return true si bloqué, false si connexion autorisée
     */
    public boolean isBlocked(String email) {
        AttemptRecord record = attempts.get(normalise(email));
        if (record == null) return false;
        if (record.lockedUntil() != null && LocalDateTime.now().isBefore(record.lockedUntil())) {
            log.warn("Login blocked for email={} — locked until {}", email, record.lockedUntil());
            return true;
        }
        return false;
    }

    /**
     * Enregistre un échec d'authentification.
     * Déclenche un blocage si MAX_ATTEMPTS est atteint.
     *
     * @param email identifiant du compte
     */
    public void registerFailure(String email) {
        String key = normalise(email);
        LocalDateTime now = LocalDateTime.now();

        AttemptRecord current = attempts.getOrDefault(key, new AttemptRecord(0, now, null));

        // Réinitialiser la fenêtre si expirée
        if (current.windowStart().isBefore(now.minusMinutes(WINDOW_MINUTES))) {
            current = new AttemptRecord(0, now, null);
        }

        int newCount = current.count() + 1;
        LocalDateTime lockedUntil = null;

        if (newCount >= MAX_ATTEMPTS) {
            lockedUntil = now.plusMinutes(LOCKOUT_MINUTES);
            log.warn("Account locked for email={} after {} failed attempts — locked until {}",
                    email, newCount, lockedUntil);
        }

        attempts.put(key, new AttemptRecord(newCount, current.windowStart(), lockedUntil));
    }

    /**
     * Remet le compteur à zéro après une connexion réussie.
     *
     * @param email identifiant du compte
     */
    public void registerSuccess(String email) {
        attempts.remove(normalise(email));
        log.debug("Login success — attempt counter reset for email={}", email);
    }

    /**
     * Retourne les secondes restantes de blocage (0 si non bloqué).
     *
     * @param email identifiant du compte
     */
    public long remainingLockoutSeconds(String email) {
        AttemptRecord record = attempts.get(normalise(email));
        if (record == null || record.lockedUntil() == null) return 0L;
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(record.lockedUntil())) return 0L;
        return java.time.Duration.between(now, record.lockedUntil()).getSeconds();
    }

    private String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
