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
 * V23 — Admin proxy for stations.
 *
 * The Angular frontend expects the admin station CRUD under
 *   /api/v1/admin/stations
 * but the canonical backend route is
 *   /api/v1/stations/internal/admin (protected by X-Internal-Secret).
 *
 * Without this proxy, every admin CRUD call from the frontend (create,
 * update, delete, list, stats, secondary points) returned 404. This
 * controller bridges the gap exactly the way AdminBannersProxyController,
 * AdminPromoCodesProxyController, AdminPaymentsProxyController, etc. do
 * for their respective services. The X-Internal-Secret header is
 * automatically attached by stationServiceWebClient.
 */
@RestController
@RequestMapping("/api/v1/admin/stations")
@RequiredArgsConstructor
@Tag(name = "Admin – Stations", description = "Frontend-facing proxy to station-service")
public class AdminStationsProxyController {

    private final WebClient stationServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // ────────────── Stations CRUD ──────────────

    @GetMapping
    @Operation(summary = "List all stations (admin view)")
    public ResponseEntity<Object> list() {
        Object body = stationServiceWebClient.get()
                .uri("/api/v1/stations")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a station by ID (admin view)")
    public ResponseEntity<Object> get(@PathVariable String id) {
        Object body = stationServiceWebClient.get()
                .uri("/api/v1/stations/" + id)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @PostMapping
    @Operation(summary = "Create a new station")
    public ResponseEntity<Object> create(@RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = stationServiceWebClient.post()
                .uri("/api/v1/stations/internal/admin")
                .header("X-User-Id", adminId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a station")
    public ResponseEntity<Object> update(@PathVariable String id,
                                         @RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = stationServiceWebClient.put()
                .uri("/api/v1/stations/internal/admin/" + id)
                .header("X-User-Id", adminId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a station")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestHeader("X-User-Id") String adminId) {
        stationServiceWebClient.delete()
                .uri("/api/v1/stations/internal/admin/" + id)
                .header("X-User-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }

    // ────────────── Stats ──────────────

    @GetMapping("/stats")
    @Operation(summary = "Aggregate station statistics for admin dashboard")
    public ResponseEntity<Object> stats() {
        Object body = stationServiceWebClient.get()
                .uri("/api/v1/stations/internal/admin/stats")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    // ────────────── Secondary points ──────────────
    // The frontend admin-station service calls these to manage drop-off points
    // attached to a primary station.

    @GetMapping("/{stationId}/secondary-points")
    @Operation(summary = "List secondary points of a station")
    public ResponseEntity<Object> listSecondaryPoints(@PathVariable String stationId) {
        Object body = stationServiceWebClient.get()
                .uri("/api/v1/stations/" + stationId + "/secondary-points")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{stationId}/secondary-points")
    @Operation(summary = "Add a secondary point to a station")
    public ResponseEntity<Object> addSecondaryPoint(@PathVariable String stationId,
                                                     @RequestBody Map<String, Object> body,
                                                     @RequestHeader("X-User-Id") String adminId) {
        Object res = stationServiceWebClient.post()
                .uri("/api/v1/stations/internal/admin/" + stationId + "/secondary-points")
                .header("X-User-Id", adminId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/secondary-points/{pointId}")
    @Operation(summary = "Update a secondary point")
    public ResponseEntity<Object> updateSecondaryPoint(@PathVariable String pointId,
                                                        @RequestBody Map<String, Object> body,
                                                        @RequestHeader("X-User-Id") String adminId) {
        Object res = stationServiceWebClient.patch()
                .uri("/api/v1/stations/internal/admin/secondary-points/" + pointId)
                .header("X-User-Id", adminId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/secondary-points/{pointId}")
    @Operation(summary = "Delete a secondary point")
    public ResponseEntity<Void> deleteSecondaryPoint(@PathVariable String pointId,
                                                      @RequestHeader("X-User-Id") String adminId) {
        stationServiceWebClient.delete()
                .uri("/api/v1/stations/internal/admin/secondary-points/" + pointId)
                .header("X-User-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }
}
