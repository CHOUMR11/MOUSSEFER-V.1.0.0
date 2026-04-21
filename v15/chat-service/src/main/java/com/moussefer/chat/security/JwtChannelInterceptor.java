package com.moussefer.chat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@Slf4j
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final SecretKey secretKey;

    public JwtChannelInterceptor(@Value("${jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders == null || authHeaders.isEmpty()) {
                log.warn("Missing Authorization header in WebSocket CONNECT");
                throw new RuntimeException("Missing token");
            }
            String bearerToken = authHeaders.get(0);
            if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                log.warn("Invalid Authorization header format");
                throw new RuntimeException("Invalid token format");
            }
            String token = bearerToken.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
                String userId = claims.get("userId", String.class);
                if (userId == null || userId.isBlank()) {
                    log.warn("JWT missing userId claim");
                    throw new RuntimeException("Invalid token");
                }
                accessor.setUser(() -> userId);
                log.debug("WebSocket authenticated: userId={}", userId);
            } catch (Exception e) {
                log.warn("JWT validation failed: {}", e.getMessage());
                throw new RuntimeException("Unauthorized");
            }
        }
        return message;
    }
}