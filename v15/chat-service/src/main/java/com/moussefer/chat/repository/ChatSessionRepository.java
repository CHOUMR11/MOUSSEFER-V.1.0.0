package com.moussefer.chat.repository;

import com.moussefer.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    Optional<ChatSession> findByReferenceId(String referenceId);

    @Modifying
    @Query("UPDATE ChatSession s SET s.active = false WHERE s.expiresAt < :now AND s.active = true")
    int deactivateExpiredSessions(LocalDateTime now);
}