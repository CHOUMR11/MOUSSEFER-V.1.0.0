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
 * Proxy controller — exposes /api/v1/admin/trajets for the frontend,
 * delegates to trajet-service's admin endpoints.
 *
 * Frontend-expected paths:
 *  - GET  /api/v1/admin/trajets       (list all)
 *  - PUT  /api/v1/admin/trajets/{id}/status   (CANCEL / ASSIGN_DRIVER)
 *  - DELETE /api/v1/admin/trajets/{id}        (cancel)
 */
@RestController
@RequestMapping("/api/v1/admin/trajets")
@RequiredArgsConstructor
@Tag(name = "Admin – Trajets", description = "Frontend-facing proxy to trajet-service admin endpoints")
public class AdminTrajetsProxyController {

    private final WebClient trajetServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @GetMapping
    @Operation(summary = "List all trajets (admin view, filterable + paginated)")
    public ResponseEntity<Object> list(@RequestParam(required = false) String status,
                                       @RequestParam(required = false) String driverId,
                                       @RequestParam(required = false) String departureCity,
                                       @RequestParam(required = false) String arrivalCity,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        StringBuilder uri = new StringBuilder("/api/v1/trajets/admin/all?page=").append(page).append("&size=").append(size);
        if (status != null) uri.append("&status=").append(status);
        if (driverId != null) uri.append("&driverId=").append(driverId);
        if (departureCity != null) uri.append("&departureCity=").append(departureCity);
        if (arrivalCity != null) uri.append("&arrivalCity=").append(arrivalCity);
        Object body = trajetServiceWebClient.get().uri(uri.toString())
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Admin: cancel a trajet")
    public ResponseEntity<Void> cancel(@PathVariable String id,
                                       @RequestHeader("X-User-Id") String adminId) {
        trajetServiceWebClient.delete()
                .uri("/api/v1/trajets/admin/" + id)
                .header("X-User-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }

    /**
     * Unified status transition endpoint matching the frontend contract.
     * Accepts: CANCELLED, ASSIGNED (with driverId in body).
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "Admin: unified trajet action (CANCELLED | ASSIGNED)")
    public ResponseEntity<Object> updateStatus(@PathVariable String id,
                                               @RequestBody Map<String, Object> body,
                                               @RequestHeader("X-User-Id") String adminId) {
        String action = String.valueOf(body.getOrDefault("status", "")).toUpperCase();
        switch (action) {
            case "CANCELLED", "CANCEL" -> {
                trajetServiceWebClient.delete()
                        .uri("/api/v1/trajets/admin/" + id)
                        .header("X-User-Id", adminId)
                        .retrieve().bodyToMono(Void.class).block(TIMEOUT);
                return ResponseEntity.noContent().build();
            }
            case "ASSIGNED", "ASSIGN", "ASSIGN_DRIVER" -> {
                String driverId = String.valueOf(body.getOrDefault("driverId", ""));
                if (driverId.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "driverId is required for ASSIGN action"));
                }
                Object res = trajetServiceWebClient.patch()
                        .uri("/api/v1/trajets/admin/" + id + "/assign-driver?driverId=" + driverId)
                        .header("X-User-Id", adminId)
                        .retrieve().bodyToMono(Object.class).block(TIMEOUT);
                return ResponseEntity.ok(res);
            }
            default -> {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid action. Expected: CANCELLED | ASSIGNED"));
            }
        }
    }
}
