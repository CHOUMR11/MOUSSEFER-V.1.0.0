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
 * Proxy controller — exposes /api/v1/admin/promocodes expected by the frontend,
 * delegates to payment-service's /api/v1/payments/promo-codes endpoints.
 *
 * This is the canonical frontend entry point for promo code CRUD.
 */
@RestController
@RequestMapping("/api/v1/admin/promocodes")
@RequiredArgsConstructor
@Tag(name = "Admin – Promo Codes", description = "Frontend-facing proxy to payment-service promo codes")
public class AdminPromoCodesProxyController {

    private final WebClient paymentServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @GetMapping
    @Operation(summary = "List all promo codes (proxy)")
    public ResponseEntity<Object> list(@RequestParam(required = false) Boolean active,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        String uri = "/api/v1/payments/promo-codes?page=" + page + "&size=" + size
                + (active != null ? "&active=" + active : "");
        Object body = paymentServiceWebClient.get().uri(uri)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    @Operation(summary = "Promo code usage statistics (proxy)")
    public ResponseEntity<Object> stats() {
        Object body = paymentServiceWebClient.get().uri("/api/v1/payments/promo-codes/stats")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        Object body = paymentServiceWebClient.get().uri("/api/v1/payments/promo-codes/" + id)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @PostMapping
    @Operation(summary = "Create a new promo code (proxy)")
    public ResponseEntity<Object> create(@RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = paymentServiceWebClient.post().uri("/api/v1/payments/promo-codes")
                .header("X-User-Id", adminId)
                .bodyValue(body).retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id,
                                         @RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = paymentServiceWebClient.patch().uri("/api/v1/payments/promo-codes/" + id)
                .header("X-User-Id", adminId)
                .bodyValue(body).retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Alias PUT → PATCH for frontend compatibility")
    public ResponseEntity<Object> updatePut(@PathVariable String id,
                                            @RequestBody Map<String, Object> body,
                                            @RequestHeader("X-User-Id") String adminId) {
        return update(id, body, adminId);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable String id,
                                         @RequestHeader("X-User-Id") String adminId) {
        paymentServiceWebClient.post().uri("/api/v1/payments/promo-codes/" + id + "/activate")
                .header("X-User-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String id,
                                           @RequestHeader("X-User-Id") String adminId) {
        paymentServiceWebClient.post().uri("/api/v1/payments/promo-codes/" + id + "/deactivate")
                .header("X-User-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestHeader("X-User-Id") String adminId) {
        paymentServiceWebClient.delete().uri("/api/v1/payments/promo-codes/" + id)
                .header("X-User-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }
}
