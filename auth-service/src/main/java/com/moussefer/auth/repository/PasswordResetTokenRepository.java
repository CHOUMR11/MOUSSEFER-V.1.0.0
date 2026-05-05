package com.moussefer.auth.repository;

import com.moussefer.auth.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByToken(String token);

    /**
     * Scheduler cleanup — delete tokens that expired at least 7 days ago
     * so the table doesn't grow forever.
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Invalidate any pending tokens for this user — called before issuing
     * a fresh one, so a user who requests multiple resets only has the
     * latest token active. Prevents replay confusion.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken p SET p.used = true, p.usedAt = :now " +
           "WHERE p.userId = :userId AND p.used = false")
    int invalidateAllForUser(@Param("userId") String userId, @Param("now") LocalDateTime now);
}
