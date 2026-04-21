package com.moussefer.chat.service;

import com.moussefer.chat.dto.ChatActivationEvent;
import com.moussefer.chat.entity.*;
import com.moussefer.chat.exception.BusinessException;
import com.moussefer.chat.exception.ResourceNotFoundException;
import com.moussefer.chat.repository.ChatMessageRepository;
import com.moussefer.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final SimpMessagingTemplate brokerTemplate;

    @Value("${chat.expiration-hours-after-departure:1}")
    private int expirationHours;

    @Transactional
    public void activateSession(ChatActivationEvent event) {
        if (sessionRepo.findByReferenceId(event.getReferenceId()).isPresent()) {
            log.warn("Session already exists for referenceId: {}", event.getReferenceId());
            return;
        }

        SessionType sessionType = "RIDE".equalsIgnoreCase(event.getType()) ?
                SessionType.RIDE : SessionType.ORGANIZED;

        LocalDateTime departureTime = event.getDepartureTime();
        LocalDateTime expiresAt = departureTime != null ?
                departureTime.plusHours(expirationHours) :
                LocalDateTime.now().plusDays(30); // fallback

        ChatSession session = ChatSession.builder()
                .referenceId(event.getReferenceId())
                .sessionType(sessionType)
                .participant1Id(event.getPassengerId())
                .participant2Id(event.getCounterpartId())
                .departureTime(departureTime)
                .expiresAt(expiresAt)
                .active(true)
                .build();

        session = sessionRepo.save(session);
        log.info("Chat session activated: ref={}, type={}", session.getReferenceId(), sessionType);

        sendSystemMessage(session.getReferenceId(), "Chat activé. Vous pouvez maintenant communiquer.");
    }

    @Transactional
    public ChatMessage sendMessage(String sessionId, String senderId, String content, MessageType type) {
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Chat session not found: " + sessionId));

        if (!session.isActive()) {
            throw new BusinessException("Chat session is closed");
        }
        if (session.getExpiresAt() != null && LocalDateTime.now().isAfter(session.getExpiresAt())) {
            session.setActive(false);
            sessionRepo.save(session);
            throw new BusinessException("Chat session has expired");
        }

        if (!session.getParticipant1Id().equals(senderId) && !session.getParticipant2Id().equals(senderId)) {
            throw new BusinessException("You are not authorized to send messages in this chat");
        }

        ChatMessage msg = ChatMessage.builder()
                .sessionId(sessionId)
                .senderId(senderId)
                .content(content)
                .type(type)
                .build();
        msg = messageRepo.save(msg);

        brokerTemplate.convertAndSend("/topic/chat/" + sessionId, msg);
        return msg;
    }

    private void sendSystemMessage(String sessionId, String content) {
        ChatMessage sysMsg = ChatMessage.builder()
                .sessionId(sessionId)
                .senderId("SYSTEM")
                .content(content)
                .type(MessageType.SYSTEM)
                .build();
        messageRepo.save(sysMsg);
        brokerTemplate.convertAndSend("/topic/chat/" + sessionId, sysMsg);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getHistory(String sessionId, String requesterId, int page, int size) {
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Chat session not found: " + sessionId));

        if (!session.getParticipant1Id().equals(requesterId) && !session.getParticipant2Id().equals(requesterId)) {
            throw new BusinessException("Access denied to this chat history");
        }

        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "sentAt"));
        return messageRepo.findBySessionIdOrderBySentAtDesc(sessionId, pageRequest).getContent();
    }

    // ─── Admin operations (called via internal endpoints, no role checks) ───
    @Transactional
    public void deleteMessage(String messageId) {
        ChatMessage message = messageRepo.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found: " + messageId));
        messageRepo.delete(message);
        log.info("Message {} deleted by admin", messageId);
    }

    @Transactional
    public void closeChatSession(String sessionId) {
        ChatSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + sessionId));
        session.setActive(false);
        sessionRepo.save(session);
        sendSystemMessage(sessionId, "Chat fermé par l'administration.");
        log.info("Chat session {} closed by admin", sessionId);
    }

    @Transactional
    public void deactivateExpiredSessions() {
        int count = sessionRepo.deactivateExpiredSessions(LocalDateTime.now());
        if (count > 0) {
            log.info("Deactivated {} expired chat sessions", count);
        }
    }
}