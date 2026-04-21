package com.moussefer.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Proxy controller — exposes /api/v1/admin/reservations for the frontend,
 * delegates to reservation-service's admin endpoints.
 *
 * Added to satisfy frontend expectations:
 *  - GET /api/v1/admin/reservations  (list)
 *  - GET /api/v1/admin/reservations/{id}  (detail)
 *  - PUT /api/v1/admin/reservations/{id}/status  (unified status transition)
 *  - GET /api/v1/admin/reservations/stats
 */
@RestController
@RequestMapping("/api/v1/admin/reservations")
@RequiredArgsConstructor
@Tag(name = "Admin – Reservations", description = "Frontend-facing proxy to reservation-service admin endpoints")
public class AdminReservationsProxyController {

    private final WebClient reservationServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @GetMapping
    @Operation(summary = "List all reservations (admin)")
    public ResponseEntity<Object> list(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String passengerId,
                                       @RequestParam(required = false) String driverId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        StringBuilder uri = new StringBuilder("/api/v1/reservations/admin/all?page=").append(page).append("&size=").append(size);
        if (status != null) uri.append("&status=").append(status);
        if (passengerId != null) uri.append("&passengerId=").append(passengerId);
        if (driverId != null) uri.append("&driverId=").append(driverId);
        Object body = reservationServiceWebClient.get().uri(uri.toString())
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public ResponseEntity<Object> stats() {
        Object body = reservationServiceWebClient.get().uri("/api/v1/reservations/admin/stats")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> detail(@PathVariable String id) {
        Object body = reservationServiceWebClient.get().uri("/api/v1/reservations/admin/" + id)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    /**
     * Unified status transition endpoint.
     * Accepts: CANCELLED (force-cancel), CONFIRMED (force-confirm), ESCALATED (force-escalate).
     * Refunds-and-cancel is a separate action triggered from the payment detail view.
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Force a reservation status transition")
    public ResponseEntity<Object> updateStatus(@PathVariable String id,
                                               @RequestBody Map<String, Object> body,
                                               @RequestHeader("X-User-Id") String adminId) {
        String targetStatus = String.valueOf(body.getOrDefault("status", "")).toUpperCase();
        String reason = String.valueOf(body.getOrDefault("reason", ""));
        String path = switch (targetStatus) {
            case "CANCELLED", "CANCEL" -> "/force-cancel";
            case "CONFIRMED", "CONFIRM" -> "/force-confirm";
            case "ESCALATED", "ESCALATE" -> "/force-escalate";
            default -> null;
        };
        if (path == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid target status. Expected: CANCELLED | CONFIRMED | ESCALATED"));
        }
        Object res = reservationServiceWebClient.post()
                .uri("/api/v1/reservations/admin/" + id + path)
                .header("X-User-Id", adminId)
                .bodyValue(Map.of("reason", reason))
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund reservation payment and cancel")
    public ResponseEntity<Object> refundAndCancel(@PathVariable String id,
                                                  @RequestBody(required = false) Map<String, Object> body,
                                                  @RequestHeader("X-User-Id") String adminId) {
        Object res = reservationServiceWebClient.post()
                .uri("/api/v1/reservations/admin/" + id + "/refund-and-cancel")
                .header("X-User-Id", adminId)
                .bodyValue(body != null ? body : Map.of())
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }
}
