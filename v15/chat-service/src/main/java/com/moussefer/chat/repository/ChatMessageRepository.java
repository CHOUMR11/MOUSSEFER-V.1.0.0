package com.moussefer.chat.repository;

import com.moussefer.chat.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    Page<ChatMessage> findBySessionIdOrderBySentAtDesc(String sessionId, Pageable pageable);
}