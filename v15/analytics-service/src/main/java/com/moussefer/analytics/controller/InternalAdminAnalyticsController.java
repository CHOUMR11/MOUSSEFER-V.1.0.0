package com.moussefer.analytics.controller;

import com.moussefer.analytics.dto.response.DashboardResponse;
import com.moussefer.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/analytics/internal/admin")
@RequiredArgsConstructor
public class InternalAdminAnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    @Operation(summary = "Internal admin: global dashboard statistics")
    public ResponseEntity<DashboardResponse> getDashboard() {
        return ResponseEntity.ok(analyticsService.getDashboard());
    }

    @GetMapping("/export")
    @Operation(summary = "Internal admin: export analytics events as CSV")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate) {
        byte[] csv = analyticsService.exportCsv(eventType, fromDate, toDate);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=analytics.csv")
                .body(csv);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Internal admin: get active spike alerts")
    public ResponseEntity<List<Map<String, Object>>> getSpikeAlerts() {
        return ResponseEntity.ok(analyticsService.detectSpikeAlerts());
    }

    @GetMapping("/top-routes")
    @Operation(summary = "Internal admin: get most popular routes")
    public ResponseEntity<List<Map<String, Object>>> getTopRoutes() {
        return ResponseEntity.ok(analyticsService.getTopRoutes());
    }
}