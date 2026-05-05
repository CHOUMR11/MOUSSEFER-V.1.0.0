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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin – Specialized Dashboards", description = "Role-specific KPI dashboards")
public class AdminDashboardController {

    private final WebClient userServiceWebClient;
    private final WebClient reservationServiceWebClient;
    private final WebClient paymentServiceWebClient;
    private final WebClient analyticsServiceWebClient;
    private final WebClient trajetServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    // ─── SUPER_ADMIN ───────────────────────────────────────────────────
    @GetMapping("/super-admin")
    public ResponseEntity<Map<String, Object>> superAdminDashboard(
            @RequestHeader("X-Admin-Role") String role) {
        requireRole(role, "SUPER_ADMIN");
        Map<String, Object> dash = new HashMap<>();
        dash.put("scope", "SUPER_ADMIN — full platform overview");
        dash.put("users", safe(() -> userServiceWebClient.get()
                .uri("/api/v1/users/internal/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("reservations", safe(() -> reservationServiceWebClient.get()
                .uri("/api/v1/reservations/internal/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("payments", safe(() -> paymentServiceWebClient.get()
                .uri("/api/v1/payments/internal/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("analytics", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/dashboard")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("quickActions", List.of(
                "Manage admin roles",
                "Create ORGANIZER accounts",
                "Review audit logs",
                "Assign/revoke admin permissions"
        ));
        return ResponseEntity.ok(dash);
    }

    // ─── OPERATIONAL_ADMIN ────────────────────────────────────────────
    @GetMapping("/operational")
    public ResponseEntity<Map<String, Object>> operationalDashboard(
            @RequestHeader("X-Admin-Role") String role) {
        requireRole(role, "OPERATIONAL_ADMIN", "SUPER_ADMIN");
        Map<String, Object> dash = new HashMap<>();
        dash.put("scope", "OPERATIONAL_ADMIN — reservations, trajets, disputes");
        dash.put("reservations", safe(() -> reservationServiceWebClient.get()
                .uri("/api/v1/reservations/internal/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("pendingReservations", safe(() -> reservationServiceWebClient.get()
                .uri("/api/v1/reservations/internal/admin/all?status=PENDING_DRIVER&size=10")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("escalatedReservations", safe(() -> reservationServiceWebClient.get()
                .uri("/api/v1/reservations/internal/admin/all?status=ESCALATED&size=10")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("disputes", safe(() -> reservationServiceWebClient.get()
                .uri("/api/v1/reservations/internal/admin/disputes/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("trajets", safe(() -> trajetServiceWebClient.get()
                .uri("/api/v1/trajets/internal/admin/all?size=20")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("quickActions", List.of(
                "Force-cancel stuck reservations",
                "Assign replacement drivers",
                "Resolve open disputes",
                "Review escalated timeouts"
        ));
        return ResponseEntity.ok(dash);
    }

    // ─── FINANCIAL_ADMIN ──────────────────────────────────────────────
    @GetMapping("/financial")
    public ResponseEntity<Map<String, Object>> financialDashboard(
            @RequestHeader("X-Admin-Role") String role) {
        requireRole(role, "FINANCIAL_ADMIN", "SUPER_ADMIN");
        Map<String, Object> dash = new HashMap<>();
        dash.put("scope", "FINANCIAL_ADMIN — revenue, commissions, driver payouts");
        dash.put("paymentStats", safe(() -> paymentServiceWebClient.get()
                .uri("/api/v1/payments/internal/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("commissions", safe(() -> paymentServiceWebClient.get()
                .uri("/api/v1/payments/internal/admin/commissions")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        dash.put("driverPayouts", safe(() -> paymentServiceWebClient.get()
                .uri("/api/v1/payments/internal/admin/driver-payouts")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        dash.put("promoCodesStats", safe(() -> paymentServiceWebClient.get()
                .uri("/api/v1/payments/internal/admin/promo-codes/stats")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        dash.put("quickActions", List.of(
                "Issue refunds",
                "Export monthly commissions CSV",
                "Manage promo codes",
                "Review failed payments"
        ));
        return ResponseEntity.ok(dash);
    }

    // ─── MODERATOR ────────────────────────────────────────────────────
    @GetMapping("/moderator")
    public ResponseEntity<Map<String, Object>> moderatorDashboard(
            @RequestHeader("X-Admin-Role") String role) {
        requireRole(role, "MODERATOR", "SUPER_ADMIN");
        Map<String, Object> dash = new HashMap<>();
        dash.put("scope", "MODERATOR — users, reviews, content");
        dash.put("userStats", safe(() -> userServiceWebClient.get()
                .uri("/api/v1/users/internal/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("pendingVerifications", safe(() -> userServiceWebClient.get()
                .uri("/api/v1/users/internal/admin/list?status=PENDING_VERIFICATION&size=20")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        dash.put("suspendedUsers", safe(() -> userServiceWebClient.get()
                .uri("/api/v1/users/internal/admin/list?status=SUSPENDED&size=20")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        dash.put("quickActions", List.of(
                "Review KYC documents",
                "Process suspension requests",
                "Delete flagged reviews",
                "Moderate chat messages"
        ));
        return ResponseEntity.ok(dash);
    }

    // ─── REPORTER ─────────────────────────────────────────────────────
    @GetMapping("/reporter")
    public ResponseEntity<Map<String, Object>> reporterDashboard(
            @RequestHeader("X-Admin-Role") String role) {
        requireRole(role, "REPORTER", "SUPER_ADMIN");
        Map<String, Object> dash = new HashMap<>();
        dash.put("scope", "REPORTER — analytics overview (read-only)");
        dash.put("analytics", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/dashboard")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("topRoutes", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/top-routes?limit=10")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        dash.put("alerts", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/alerts")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        dash.put("quickActions", List.of(
                "View monthly reports",
                "Export analytics CSV",
                "Compare period-over-period metrics"
        ));
        return ResponseEntity.ok(dash);
    }

    // ─── AUDITEUR ─────────────────────────────────────────────────────
    @GetMapping("/auditor")
    public ResponseEntity<Map<String, Object>> auditorDashboard(
            @RequestHeader("X-Admin-Role") String role) {
        requireRole(role, "AUDITEUR", "SUPER_ADMIN");
        Map<String, Object> dash = new HashMap<>();
        dash.put("scope", "AUDITEUR — audit logs, compliance");
        dash.put("analytics", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/dashboard")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        dash.put("quickActions", List.of(
                "Review audit trail",
                "Export compliance report",
                "Check admin activity log",
                "Trace sensitive data access"
        ));
        dash.put("auditLogsEndpoint", "/api/v1/admin/audit-logs");
        return ResponseEntity.ok(dash);
    }

    // ─── MY DASHBOARD ────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> myDashboard(
            @RequestHeader("X-Admin-Role") String role) {
        // Ensure a non-null value is passed downstream
        String safeRole = role != null ? role.toUpperCase() : "";
        return switch (safeRole) {
            case "SUPER_ADMIN"       -> superAdminDashboard(safeRole);
            case "OPERATIONAL_ADMIN" -> operationalDashboard(safeRole);
            case "FINANCIAL_ADMIN"   -> financialDashboard(safeRole);
            case "MODERATOR"         -> moderatorDashboard(safeRole);
            case "REPORTER"          -> reporterDashboard(safeRole);
            case "AUDITEUR"          -> auditorDashboard(safeRole);
            default -> ResponseEntity.status(403).body(Map.of(
                    "error", "Unknown admin role",
                    "received", role));
        };
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    private void requireRole(String actual, String... allowed) {
        if (actual == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Admin role required");
        }
        String norm = actual.toUpperCase();
        for (String a : allowed) {
            if (a.equalsIgnoreCase(norm)) return;
        }
        throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Access denied for role " + actual + ". Allowed: " + String.join(", ", allowed));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T safe(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Dashboard subservice call failed: {}", e.getMessage());
            return (T) Map.of("error", "service unavailable", "message", e.getMessage());
        }
    }
}