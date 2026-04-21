package com.moussefer.chat.controller;

import com.moussefer.chat.dto.ChatActivationEvent;
import com.moussefer.chat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/internal/admin")
@RequiredArgsConstructor
@Slf4j
public class InternalAdminChatController {

    private final ChatService chatService;

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Internal admin: delete a message")
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
        chatService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{sessionId}/close")
    @Operation(summary = "Internal admin: close a chat session")
    public ResponseEntity<Void> closeChatSession(@PathVariable String sessionId) {
        chatService.closeChatSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activate")
    @Operation(summary = "Internal admin: manually activate a chat session")
    public ResponseEntity<Void> adminActivate(@RequestBody ChatActivationEvent event) {
        chatService.activateSession(event);
        return ResponseEntity.ok().build();
    }
}