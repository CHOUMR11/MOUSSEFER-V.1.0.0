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
 * Proxy controller — exposes /api/v1/admin/payments for the frontend,
 * delegates to payment-service's admin endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
@Tag(name = "Admin – Payments", description = "Frontend-facing proxy to payment-service admin endpoints")
public class AdminPaymentsProxyController {

    private final WebClient paymentServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @GetMapping
    @Operation(summary = "List all payments (admin)")
    public ResponseEntity<Object> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        StringBuilder uri = new StringBuilder("/api/v1/payments/admin?page=").append(page).append("&size=").append(size);
        if (status != null) uri.append("&status=").append(status);
        Object body = paymentServiceWebClient.get().uri(uri.toString())
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stats")
    public ResponseEntity<Object> stats() {
        Object body = paymentServiceWebClient.get().uri("/api/v1/payments/admin/stats")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<Object> detail(@PathVariable String paymentId) {
        Object body = paymentServiceWebClient.get().uri("/api/v1/payments/admin/" + paymentId)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/commissions")
    public ResponseEntity<Object> commissions(@RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        StringBuilder uri = new StringBuilder("/api/v1/payments/admin/commissions?");
        if (from != null) uri.append("from=").append(from).append("&");
        if (to != null) uri.append("to=").append(to);
        Object body = paymentServiceWebClient.get().uri(uri.toString())
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/driver-payouts")
    public ResponseEntity<Object> driverPayouts() {
        Object body = paymentServiceWebClient.get().uri("/api/v1/payments/admin/driver-payouts")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to) {
        StringBuilder uri = new StringBuilder("/api/v1/payments/admin/export?");
        if (status != null) uri.append("status=").append(status).append("&");
        if (from != null) uri.append("from=").append(from).append("&");
        if (to != null) uri.append("to=").append(to);
        byte[] body = paymentServiceWebClient.get().uri(uri.toString())
                .retrieve().bodyToMono(byte[].class).block(TIMEOUT);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"payments.csv\"")
                .body(body);
    }

    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund a payment (partial or total)")
    public ResponseEntity<Object> refund(@PathVariable String paymentId,
                                         @RequestBody(required = false) Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = paymentServiceWebClient.post()
                .uri("/api/v1/payments/refund/" + paymentId)
                .header("X-User-Id", adminId)
                .bodyValue(body != null ? body : Map.of())
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{paymentId}/status")
    @Operation(summary = "Update payment status — supports REFUNDED via Stripe refund")
    public ResponseEntity<Object> updateStatus(@PathVariable String paymentId,
                                               @RequestBody Map<String, Object> body,
                                               @RequestHeader("X-User-Id") String adminId) {
        String status = String.valueOf(body.getOrDefault("status", "")).toUpperCase();
        if ("REFUNDED".equals(status) || "REFUND".equals(status)) {
            return refund(paymentId, body, adminId);
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid status transition. Only REFUNDED is supported via admin API.",
                "received", status,
                "hint", "Payment statuses PENDING → SUCCEEDED / FAILED are set by Stripe webhook"));
    }
}
