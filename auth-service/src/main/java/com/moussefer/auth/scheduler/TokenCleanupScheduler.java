package com.moussefer.auth.scheduler;

import com.moussefer.auth.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupScheduler {

    private final PasswordResetTokenRepository tokenRepository;

    /**
     * Supprime les tokens de réinitialisation expirés depuis plus de 7 jours.
     * Exécution quotidienne à 3h00 du matin.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int deleted = tokenRepository.deleteExpiredBefore(cutoff);
        if (deleted > 0) {
            log.info("Deleted {} expired password reset tokens (expired before {})", deleted, cutoff);
        }
    }
}