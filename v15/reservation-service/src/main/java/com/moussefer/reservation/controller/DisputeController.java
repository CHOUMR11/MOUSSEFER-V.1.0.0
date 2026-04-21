package com.moussefer.reservation.controller;

import com.moussefer.reservation.entity.Dispute;
import com.moussefer.reservation.entity.DisputeStatus;
import com.moussefer.reservation.exception.BusinessException;
import com.moussefer.reservation.exception.ResourceNotFoundException;
import com.moussefer.reservation.repository.DisputeRepository;
import com.moussefer.reservation.repository.ReservationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations/disputes")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Disputes", description = "Gestion des litiges pour passagers et chauffeurs")
public class DisputeController {

    private final DisputeRepository disputeRepository;
    private final ReservationRepository reservationRepository;

    @PostMapping
    @Operation(summary = "Soumettre un litige sur une réservation")
    public ResponseEntity<Dispute> createDispute(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam String reservationId,
            @RequestParam String reportedUserId,
            @RequestParam String category,
            @RequestParam String description) {
        var reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        if (!reservation.getPassengerId().equals(userId) && !reservation.getDriverId().equals(userId)) {
            throw new BusinessException("You are not part of this reservation");
        }
        Dispute dispute = Dispute.builder()
                .reservationId(reservationId)
                .reporterId(userId)
                .reporterRole(role)
                .reportedUserId(reportedUserId)
                .category(category)
                .description(description)
                .build();
        dispute = disputeRepository.save(dispute);
        log.info("Dispute created: id={} reservation={} by={}", dispute.getId(), reservationId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dispute);
    }

    @GetMapping("/my")
    @Operation(summary = "Lister mes litiges")
    public ResponseEntity<Page<Dispute>> getMyDisputes(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disputeRepository.findByReporterId(userId, pageable));
    }

    @GetMapping("/reservation/{reservationId}")
    @Operation(summary = "Lister les litiges d'une réservation")
    public ResponseEntity<List<Dispute>> getDisputesByReservation(@PathVariable String reservationId) {
        return ResponseEntity.ok(disputeRepository.findByReservationId(reservationId));
    }
}