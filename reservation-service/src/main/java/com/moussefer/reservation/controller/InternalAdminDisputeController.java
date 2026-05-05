package com.moussefer.reservation.controller;

import com.moussefer.reservation.entity.Dispute;
import com.moussefer.reservation.entity.DisputeStatus;
import com.moussefer.reservation.exception.ResourceNotFoundException;
import com.moussefer.reservation.repository.DisputeRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservations/internal/admin/disputes")
@RequiredArgsConstructor
@Slf4j
public class InternalAdminDisputeController {

    private final DisputeRepository disputeRepository;

    @GetMapping("/all")
    @Operation(summary = "Internal admin: list all disputes")
    public ResponseEntity<Page<Dispute>> listAll(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        if (status != null) {
            return ResponseEntity.ok(disputeRepository.findByStatus(DisputeStatus.valueOf(status.toUpperCase()), pageable));
        }
        return ResponseEntity.ok(disputeRepository.findAll(pageable));
    }

    @PostMapping("/{disputeId}/assign")
    @Operation(summary = "Internal admin: assign a dispute to admin")
    public ResponseEntity<Dispute> assignDispute(
            @PathVariable String disputeId,
            @RequestHeader("X-Admin-Id") String adminId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));
        dispute.setAdminId(adminId);
        dispute.setStatus(DisputeStatus.IN_PROGRESS);
        disputeRepository.save(dispute);
        log.info("Dispute {} assigned to admin {}", disputeId, adminId);
        return ResponseEntity.ok(dispute);
    }

    @PostMapping("/{disputeId}/resolve")
    @Operation(summary = "Internal admin: resolve a dispute")
    public ResponseEntity<Dispute> resolveDispute(
            @PathVariable String disputeId,
            @RequestHeader("X-Admin-Id") String adminId,
            @RequestParam String resolution,
            @RequestParam(defaultValue = "RESOLVED") String outcome) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new ResourceNotFoundException("Dispute not found: " + disputeId));
        dispute.setAdminId(adminId);
        dispute.setResolution(resolution);
        dispute.setStatus(DisputeStatus.valueOf(outcome.toUpperCase()));
        dispute.setResolvedAt(LocalDateTime.now());
        disputeRepository.save(dispute);
        log.info("Dispute {} resolved by admin {} with outcome {}", disputeId, adminId, outcome);
        return ResponseEntity.ok(dispute);
    }

    @GetMapping("/stats")
    @Operation(summary = "Internal admin: dispute statistics")
    public ResponseEntity<Map<String, Object>> getDisputeStats() {
        return ResponseEntity.ok(Map.of(
                "totalOpen", disputeRepository.countByStatus(DisputeStatus.OPEN),
                "totalInProgress", disputeRepository.countByStatus(DisputeStatus.IN_PROGRESS),
                "totalResolved", disputeRepository.countByStatus(DisputeStatus.RESOLVED),
                "totalRejected", disputeRepository.countByStatus(DisputeStatus.REJECTED),
                "totalClosed", disputeRepository.countByStatus(DisputeStatus.CLOSED)
        ));
    }
}