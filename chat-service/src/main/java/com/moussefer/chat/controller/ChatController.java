package com.moussefer.chat.controller;

import com.moussefer.chat.entity.ChatMessage;
import com.moussefer.chat.entity.MessageType;
import com.moussefer.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat", description = "WebSocket chat and history management (user-facing)")
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat/{sessionId}/send")
    public void sendMessage(@DestinationVariable String sessionId,
                            @Payload Map<String, String> payload,
                            Principal principal) {
        String senderId = principal.getName();
        String content = payload.get("content");
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }
        chatService.sendMessage(sessionId, senderId, content, MessageType.TEXT);
    }

    @GetMapping("/api/v1/chat/{sessionId}/history")
    @Operation(summary = "Get chat history for a session")
    public ResponseEntity<List<ChatMessage>> getHistory(
            @PathVariable String sessionId,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatService.getHistory(sessionId, userId, page, size));
    }
}