package com.moussefer.reservation.controller;

import com.moussefer.reservation.dto.response.ReservationResponse;
import com.moussefer.reservation.service.ReservationService;
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
    public ResponseEntity<Page<ReservationResponse>> listAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String passengerId,
            @RequestParam(required = false) String driverId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(service.adminListAll(status, passengerId, driverId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.adminGetById(id));
    }

    @PostMapping("/{id}/force-cancel")
    public ResponseEntity<Void> forceCancel(@PathVariable String id,
                                            @RequestParam(required = false) String reason) {
        service.adminForceCancel(id, reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/force-confirm")
    public ResponseEntity<Void> forceConfirm(@PathVariable String id) {
        service.adminForceConfirm(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/force-escalate")
    public ResponseEntity<Void> forceEscalate(@PathVariable String id) {
        service.adminForceEscalate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/refund-and-cancel")
    public ResponseEntity<Void> refundAndCancel(@PathVariable String id,
                                                @RequestParam(required = false) String reason) {
        service.refundAndCancel(id, reason);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable String id,
                                             @RequestBody Map<String, String> body) {
        String target = body.getOrDefault("status", "").toUpperCase();
        String reason = body.get("reason");
        switch (target) {
            case "CANCELLED", "CANCEL" -> service.adminForceCancel(id, reason);
            case "CONFIRMED", "CONFIRM" -> service.adminForceConfirm(id);
            case "ESCALATED", "ESCALATE" -> service.adminForceEscalate(id);
            default -> throw new IllegalArgumentException("Invalid target status: " + target);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(service.getReservationStats());
    }
}