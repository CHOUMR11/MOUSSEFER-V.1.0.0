package com.moussefer.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS     = 5;
    private static final int LOCKOUT_MINUTES  = 15;
    private static final int WINDOW_MINUTES   = 15;

    private record AttemptRecord(int count, LocalDateTime windowStart, LocalDateTime lockedUntil) {}

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String email) {
        AttemptRecord record = attempts.get(normalise(email));
        if (record == null) return false;
        if (record.lockedUntil() != null && LocalDateTime.now().isBefore(record.lockedUntil())) {
            log.warn("Login blocked for email={} — locked until {}", email, record.lockedUntil());
            return true;
        }
        return false;
    }

    public void registerFailure(String email) {
        String key = normalise(email);
        LocalDateTime now = LocalDateTime.now();

        attempts.compute(key, (k, current) -> {
            if (current == null || current.windowStart().isBefore(now.minusMinutes(WINDOW_MINUTES))) {
                current = new AttemptRecord(0, now, null);
            }
            int newCount = current.count() + 1;
            LocalDateTime lockedUntil = null;
            if (newCount >= MAX_ATTEMPTS) {
                lockedUntil = now.plusMinutes(LOCKOUT_MINUTES);
                log.warn("Account locked for email={} after {} failed attempts — locked until {}",
                        email, newCount, lockedUntil);
            }
            return new AttemptRecord(newCount, current.windowStart(), lockedUntil);
        });
    }

    public void registerSuccess(String email) {
        attempts.remove(normalise(email));
        log.debug("Login success — attempt counter reset for email={}", email);
    }

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