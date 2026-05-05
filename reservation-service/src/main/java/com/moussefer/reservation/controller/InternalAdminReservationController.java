package com.moussefer.reservation.controller;

import com.moussefer.reservation.dto.response.ReservationResponse;
import com.moussefer.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservations/internal/admin")
@RequiredArgsConstructor
public class InternalAdminReservationController {

    private final ReservationService service;

    @GetMapping("/all")
    @Operation(summary = "Internal admin: list all reservations with filters")
    public ResponseEntity<Page<ReservationResponse>> listAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String passengerId,
            @RequestParam(required = false) String driverId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.adminListAll(status, passengerId, driverId, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Internal admin: get reservation details")
    public ResponseEntity<ReservationResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.adminGetById(id));
    }

    @PostMapping("/{id}/force-cancel")
    @Operation(summary = "Internal admin: force cancel any reservation")
    public ResponseEntity<Void> forceCancel(
            @PathVariable String id,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "X-Admin-Id", defaultValue = "SYSTEM") String adminId) {
        service.adminForceCancel(adminId, id, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/force-confirm")
    @Operation(summary = "Internal admin: force confirm a reservation (skip payment)")
    public ResponseEntity<Void> forceConfirm(
            @PathVariable String id,
            @RequestHeader(value = "X-Admin-Id", defaultValue = "SYSTEM") String adminId) {
        service.adminForceConfirm(adminId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/force-escalate")
    @Operation(summary = "Internal admin: manually escalate a reservation")
    public ResponseEntity<Void> forceEscalate(
            @PathVariable String id,
            @RequestHeader(value = "X-Admin-Id", defaultValue = "SYSTEM") String adminId) {
        service.adminForceEscalate(adminId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refund-and-cancel")
    @Operation(summary = "Internal admin: refund and cancel a confirmed reservation")
    public ResponseEntity<Void> refundAndCancel(
            @PathVariable String id,
            @RequestParam(required = false) String reason,
            @RequestHeader(value = "X-Admin-Id", defaultValue = "SYSTEM") String adminId) {
        service.refundAndCancel(adminId, id, reason);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Internal admin: unified status transition")
    public ResponseEntity<Void> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String target = body.getOrDefault("status", "").toUpperCase();
        String reason = body.get("reason");
        service.adminUpdateStatus(id, target, reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(summary = "Internal admin: reservation statistics")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(service.getReservationStats());
    }
}