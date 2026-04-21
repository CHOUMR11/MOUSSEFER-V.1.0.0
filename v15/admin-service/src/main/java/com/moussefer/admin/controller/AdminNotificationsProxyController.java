package com.moussefer.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Admin-facing proxy for sending manual notifications to users (M02 feature).
 *
 * The notification-service already consumes Kafka events for business-triggered
 * notifications (reservation confirmed, payment failed, etc.). This proxy adds
 * the admin-initiated path: a moderator or support agent can send an in-app
 * or email message directly to a specific user.
 *
 * Endpoints:
 *  - POST /api/v1/admin/notifications/send       (single user)
 *  - POST /api/v1/admin/notifications/broadcast  (multiple users)
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin – Notifications", description = "Send manual notifications to users (moderator/support)")
public class AdminNotificationsProxyController {

    private final WebClient notificationServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @PostMapping("/send")
    @Operation(summary = "Send a manual notification to a specific user")
    public ResponseEntity<Object> sendToUser(@RequestBody Map<String, Object> body,
                                             @RequestHeader("X-User-Id") String adminId) {
        if (!body.containsKey("userId") || !body.containsKey("title") || !body.containsKey("body")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "userId, title, and body are required fields"));
        }
        Object res = notificationServiceWebClient.post()
                .uri("/api/v1/notifications/internal/admin/send")
                .header("X-Admin-Id", adminId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        log.info("Admin {} sent notification to user {}", adminId, body.get("userId"));
        return ResponseEntity.ok(res);
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Broadcast a notification to multiple users")
    public ResponseEntity<Object> broadcast(@RequestBody Map<String, Object> body,
                                            @RequestHeader("X-User-Id") String adminId) {
        if (!body.containsKey("userIds") || !body.containsKey("title") || !body.containsKey("body")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "userIds (array), title, and body are required fields"));
        }
        Object res = notificationServiceWebClient.post()
                .uri("/api/v1/notifications/internal/admin/broadcast")
                .header("X-Admin-Id", adminId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        log.info("Admin {} broadcast notification to {} users", adminId,
                body.get("userIds") instanceof java.util.List l ? l.size() : 0);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/templates")
    @Operation(summary = "List predefined notification templates available for admin use")
    public ResponseEntity<Object> templates() {
        Map<String, Object> templates = new HashMap<>();
        templates.put("account_suspended", Map.of(
                "title", "Votre compte a été suspendu",
                "body", "Bonjour, votre compte a été temporairement suspendu. Contactez le support pour plus d'informations.",
                "type", "IN_APP"));
        templates.put("account_reactivated", Map.of(
                "title", "Votre compte est réactivé",
                "body", "Bonne nouvelle ! Votre compte Moussefer a été réactivé. Vous pouvez à nouveau réserver des trajets.",
                "type", "IN_APP"));
        templates.put("document_rejected", Map.of(
                "title", "Documents à compléter",
                "body", "Un de vos documents n'a pas été validé. Merci de le soumettre à nouveau depuis votre profil.",
                "type", "EMAIL"));
        templates.put("promo_announcement", Map.of(
                "title", "Offre spéciale Moussefer",
                "body", "Profitez de 20% de réduction sur votre prochain trajet avec le code PROMO20.",
                "type", "IN_APP"));
        templates.put("service_maintenance", Map.of(
                "title", "Maintenance programmée",
                "body", "Une maintenance est prévue. Le service sera indisponible pendant environ 30 minutes.",
                "type", "IN_APP"));
        return ResponseEntity.ok(templates);
    }
}
