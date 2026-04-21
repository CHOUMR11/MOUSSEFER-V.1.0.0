package com.moussefer.notification.controller;

import com.moussefer.notification.entity.Notification;
import com.moussefer.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Validated
@Tag(name = "Notifications", description = "Gestion des notifications utilisateur")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Récupérer les notifications de l'utilisateur (paginated)")
    public ResponseEntity<List<Notification>> getMyNotifications(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(service.getUserNotifications(userId, page, size));
    }

    @PostMapping("/read-all")
    @Operation(summary = "Marquer toutes les notifications comme lues")
    public ResponseEntity<Void> markAllRead(@RequestHeader("X-User-Id") String userId) {
        service.markAllRead(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marquer une notification spécifique comme lue")
    public ResponseEntity<Void> markOneRead(@PathVariable String id,
                                            @RequestHeader("X-User-Id") String userId) {
        boolean success = service.markOneRead(id, userId);
        return success ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une notification spécifique")
    public ResponseEntity<Void> deleteOneNotification(@PathVariable String id,
                                                      @RequestHeader("X-User-Id") String userId) {
        boolean deleted = service.deleteOneNotification(id, userId);
        return deleted ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @DeleteMapping("/all")
    @Operation(summary = "Supprimer toutes les notifications de l'utilisateur")
    public ResponseEntity<Void> deleteAllNotifications(@RequestHeader("X-User-Id") String userId) {
        service.deleteAllNotifications(userId);
        return ResponseEntity.noContent().build();
    }

    // ─── Availability Alert Subscriptions (UC-31) ────────────────────
    @PostMapping("/alerts/subscribe")
    @Operation(summary = "S'abonner à une alerte de disponibilité de trajet")
    public ResponseEntity<com.moussefer.notification.entity.AlertSubscription> subscribeAlert(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam String departureCity,
            @RequestParam String arrivalCity,
            @RequestParam(required = false) String desiredDate,
            @RequestParam(defaultValue = "1") int minSeats) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.subscribeToAlert(userId, departureCity, arrivalCity, desiredDate, minSeats));
    }

    @GetMapping("/alerts/my")
    @Operation(summary = "Lister mes alertes actives")
    public ResponseEntity<java.util.List<com.moussefer.notification.entity.AlertSubscription>> getMyAlerts(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(service.getMyActiveAlerts(userId));
    }

    @DeleteMapping("/alerts/{alertId}")
    @Operation(summary = "Désactiver une alerte")
    public ResponseEntity<Void> unsubscribeAlert(
            @PathVariable String alertId,
            @RequestHeader("X-User-Id") String userId) {
        service.unsubscribeAlert(alertId, userId);
        return ResponseEntity.noContent().build();
    }

    // ==================== INTERNAL ADMIN ENDPOINTS ====================
    // Called only by admin-service via WebClient, protected by X-Internal-Secret.

    @PostMapping("/internal/admin/send")
    @Operation(summary = "Internal: send a manual notification to a user (called by admin-service)")
    public ResponseEntity<java.util.Map<String, Object>> adminSendToUser(
            @RequestHeader(value = "X-Admin-Id", required = false) String adminId,
            @RequestBody java.util.Map<String, Object> body) {
        String userId = String.valueOf(body.getOrDefault("userId", ""));
        String title = String.valueOf(body.getOrDefault("title", ""));
        String messageBody = String.valueOf(body.getOrDefault("body", ""));
        String typeStr = String.valueOf(body.getOrDefault("type", "IN_APP")).toUpperCase();
        String email = body.get("email") != null ? String.valueOf(body.get("email")) : null;

        if (userId.isBlank() || title.isBlank() || messageBody.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "userId, title, and body are required"));
        }

        com.moussefer.notification.entity.NotificationType type;
        try {
            type = com.moussefer.notification.entity.NotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "Invalid type. Expected: IN_APP | EMAIL | PUSH_FCM"));
        }

        service.send(userId, title, messageBody, type,
                adminId, "ADMIN_MANUAL", email);

        return ResponseEntity.ok(java.util.Map.of(
                "status", "sent",
                "userId", userId,
                "type", type.name(),
                "sentBy", adminId != null ? adminId : "system"));
    }

    @PostMapping("/internal/admin/broadcast")
    @Operation(summary = "Internal: broadcast a notification to multiple users")
    public ResponseEntity<java.util.Map<String, Object>> adminBroadcast(
            @RequestHeader(value = "X-Admin-Id", required = false) String adminId,
            @RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        java.util.List<String> userIds = body.get("userIds") instanceof java.util.List list
                ? (java.util.List<String>) list : java.util.List.of();
        String title = String.valueOf(body.getOrDefault("title", ""));
        String messageBody = String.valueOf(body.getOrDefault("body", ""));
        String typeStr = String.valueOf(body.getOrDefault("type", "IN_APP")).toUpperCase();

        if (userIds.isEmpty() || title.isBlank() || messageBody.isBlank()) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "userIds (non-empty), title, and body are required"));
        }

        com.moussefer.notification.entity.NotificationType type;
        try {
            type = com.moussefer.notification.entity.NotificationType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", "Invalid type. Expected: IN_APP | EMAIL | PUSH_FCM"));
        }

        int sent = 0;
        for (String uid : userIds) {
            try {
                service.send(uid, title, messageBody, type, adminId, "ADMIN_BROADCAST", null);
                sent++;
            } catch (Exception ignore) { /* continue broadcasting */ }
        }

        return ResponseEntity.ok(java.util.Map.of(
                "status", "broadcast_sent",
                "totalRecipients", userIds.size(),
                "successful", sent,
                "type", type.name()));
    }
}