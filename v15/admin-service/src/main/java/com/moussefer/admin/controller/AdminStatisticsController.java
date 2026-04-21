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

@RestController
@RequestMapping("/api/v1/admin/statistics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin – Statistics", description = "Unified dashboard statistics aggregator")
public class AdminStatisticsController {

    private final WebClient userServiceWebClient;
    private final WebClient reservationServiceWebClient;
    private final WebClient paymentServiceWebClient;
    private final WebClient analyticsServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @GetMapping
    @Operation(summary = "Unified dashboard statistics")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> result = new HashMap<>();
        result.put("users", safe(() -> userServiceWebClient.get()
                .uri("/api/v1/users/internal/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        result.put("reservations", safe(() -> reservationServiceWebClient.get()
                .uri("/api/v1/reservations/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        result.put("payments", safe(() -> paymentServiceWebClient.get()
                .uri("/api/v1/payments/admin/stats")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        // ✅ FIX: use internal admin endpoint
        result.put("analytics", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/dashboard")
                .retrieve().bodyToMono(Map.class).block(TIMEOUT)));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/recent-activity")
    @Operation(summary = "Recent activity feed for admin dashboard home")
    public ResponseEntity<Map<String, Object>> recentActivity(
            @RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> result = new HashMap<>();
        // ✅ FIX: use internal admin endpoints
        result.put("topRoutes", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/top-routes?limit=" + limit)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        result.put("alerts", safe(() -> analyticsServiceWebClient.get()
                .uri("/api/v1/analytics/internal/admin/alerts")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT)));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/charts")
    @Operation(summary = "Chart-ready statistics (labels + datasets for Chart.js)")
    public ResponseEntity<Map<String, Object>> charts(
            @RequestParam(defaultValue = "6") int months) {
        Map<String, Object> result = new HashMap<>();
        result.put("reservationsByMonth", buildMonthSeries(months, "reservations"));
        result.put("revenueByMonth", buildMonthSeries(months, "revenue"));
        result.put("newUsersByMonth", buildMonthSeries(months, "newUsers"));
        result.put("reservationStatusPie", buildStatusPie());
        result.put("topRoutesBar", buildTopRoutesBar());
        return ResponseEntity.ok(result);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> buildMonthSeries(int monthsBack, String metric) {
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Number> data = new java.util.ArrayList<>();
        java.time.LocalDate now = java.time.LocalDate.now();
        try {
            // ✅ FIX: use internal admin endpoint
            Object rawDashboard = analyticsServiceWebClient.get()
                    .uri("/api/v1/analytics/internal/admin/dashboard?months=" + monthsBack)
                    .retrieve().bodyToMono(Object.class).block(TIMEOUT);
            if (rawDashboard instanceof Map dashMap && dashMap.get(metric + "ByMonth") instanceof java.util.List list) {
                for (Object item : list) {
                    if (item instanceof Map entry) {
                        labels.add(String.valueOf(entry.get("month")));
                        Object val = entry.getOrDefault("value", entry.get("count"));
                        data.add(val instanceof Number n ? n : 0);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Analytics service returned no data for {}, generating baseline", metric);
        }
        if (labels.isEmpty()) {
            for (int i = monthsBack - 1; i >= 0; i--) {
                labels.add(now.minusMonths(i).getMonth().name().substring(0, 3));
                data.add(0);
            }
        }
        return Map.of(
                "labels", labels,
                "datasets", java.util.List.of(Map.of(
                        "label", metric,
                        "data", data,
                        "backgroundColor", "#1A5276",
                        "borderColor", "#1A5276"
                ))
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> buildStatusPie() {
        try {
            Object raw = reservationServiceWebClient.get()
                    .uri("/api/v1/reservations/admin/stats")
                    .retrieve().bodyToMono(Object.class).block(TIMEOUT);
            if (raw instanceof Map stats) {
                java.util.List<String> labels = java.util.List.of(
                        "CONFIRMED", "PENDING_DRIVER", "CANCELLED", "ESCALATED", "REFUSED");
                java.util.List<Number> data = new java.util.ArrayList<>();
                for (String s : labels) {
                    Object v = stats.get(s.toLowerCase());
                    if (v == null) v = stats.get(s);
                    data.add(v instanceof Number n ? n : 0);
                }
                return Map.of("labels", labels, "data", data,
                        "backgroundColor", java.util.List.of("#1E8449","#F39C12","#C0392B","#7F8C8D","#E74C3C"));
            }
        } catch (Exception e) {
            log.debug("Status pie fallback: {}", e.getMessage());
        }
        return Map.of("labels", java.util.List.of("CONFIRMED","PENDING_DRIVER","CANCELLED","ESCALATED","REFUSED"),
                "data", java.util.List.of(0,0,0,0,0),
                "backgroundColor", java.util.List.of("#1E8449","#F39C12","#C0392B","#7F8C8D","#E74C3C"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> buildTopRoutesBar() {
        try {
            // ✅ FIX: use internal admin endpoint
            Object raw = analyticsServiceWebClient.get()
                    .uri("/api/v1/analytics/internal/admin/top-routes?limit=5")
                    .retrieve().bodyToMono(Object.class).block(TIMEOUT);
            if (raw instanceof java.util.List list) {
                java.util.List<String> labels = new java.util.ArrayList<>();
                java.util.List<Number> data = new java.util.ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map entry) {
                        labels.add(entry.getOrDefault("route", entry.get("name")) + "");
                        Object v = entry.getOrDefault("count", entry.get("value"));
                        data.add(v instanceof Number n ? n : 0);
                    }
                }
                if (!labels.isEmpty()) {
                    return Map.of("labels", labels,
                            "datasets", java.util.List.of(Map.of(
                                    "label", "Réservations",
                                    "data", data,
                                    "backgroundColor", "#1A5276")));
                }
            }
        } catch (Exception ignore) {}
        return Map.of("labels", java.util.List.of(), "datasets", java.util.List.of());
    }

    @SuppressWarnings("unchecked")
    private <T> T safe(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.warn("Statistics subservice call failed: {}", e.getMessage());
            return (T) Map.of("error", "service unavailable", "message", e.getMessage());
        }
    }
}