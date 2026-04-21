package com.moussefer.reservation.controller;

import com.moussefer.reservation.dto.request.CreateReservationRequest;
import com.moussefer.reservation.dto.response.ReservationPricingResponse;
import com.moussefer.reservation.dto.response.ReservationResponse;
import com.moussefer.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Reservation management for passengers and drivers")
public class ReservationController {

    private final ReservationService service;

    // ==================== Passenger endpoints ====================
    @PostMapping
    @Operation(summary = "Passenger: create a reservation request")
    public ResponseEntity<ReservationResponse> create(
            @RequestHeader("X-User-Id") String passengerId,
            @Valid @RequestBody CreateReservationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(passengerId, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Passenger: cancel a pending reservation")
    public ResponseEntity<Void> cancel(
            @RequestHeader("X-User-Id") String passengerId,
            @PathVariable String id) {
        service.cancel(passengerId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    @Operation(summary = "Passenger: get my reservation history")
    public ResponseEntity<List<ReservationResponse>> myHistory(
            @RequestHeader("X-User-Id") String passengerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getPassengerHistory(passengerId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Passenger/Driver: get reservation details")
    public ResponseEntity<ReservationResponse> getById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String id) {
        return ResponseEntity.ok(service.getById(userId, id));
    }

    @GetMapping("/{id}/pricing")
    @Operation(summary = "Passenger: get pricing info for own reservation")
    public ResponseEntity<ReservationPricingResponse> getPricing(
            @RequestHeader("X-User-Id") String passengerId,
            @PathVariable String id) {
        return ResponseEntity.ok(service.getPricingForPassenger(id, passengerId));
    }

    // ==================== Driver endpoints ====================
    @PostMapping("/{id}/accept")
    @Operation(summary = "Driver: accept a reservation")
    public ResponseEntity<ReservationResponse> accept(
            @RequestHeader("X-User-Id") String driverId,
            @PathVariable String id) {
        return ResponseEntity.ok(service.driverAccept(driverId, id));
    }

    @PostMapping("/{id}/refuse")
    @Operation(summary = "Driver: refuse a reservation")
    public ResponseEntity<ReservationResponse> refuse(
            @RequestHeader("X-User-Id") String driverId,
            @PathVariable String id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(service.driverRefuse(driverId, id, reason));
    }

    @GetMapping("/driver/pending")
    @Operation(summary = "Driver: get pending reservations")
    public ResponseEntity<List<ReservationResponse>> driverPending(
            @RequestHeader("X-User-Id") String driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.getDriverPending(driverId, page, size));
    }

    // ==================== Internal endpoints (service-to-service) ====================
    @GetMapping("/internal/{id}/pricing")
    @Operation(summary = "Internal: get pricing for payment-service")
    public ResponseEntity<ReservationPricingResponse> internalPricing(
            @PathVariable String id,
            @RequestParam String passengerId) {
        return ResponseEntity.ok(service.getPricingForPassenger(id, passengerId));
    }

    @GetMapping("/internal/check")
    @Operation(summary = "Internal: check confirmed reservation (for avis-service)")
    public ResponseEntity<Boolean> internalCheckConfirmed(
            @RequestParam String passengerId,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String trajetId,
            @RequestParam(required = false) String reservationId) {
        if (reservationId != null && !reservationId.isBlank()) {
            var r = service.adminGetById(reservationId);
            boolean match = r.getPassengerId().equals(passengerId) && "CONFIRMED".equals(r.getStatus());
            // ✅ Bug #12: also verify driverId if provided
            if (match && driverId != null && !driverId.isBlank()) {
                match = r.getDriverId().equals(driverId);
            }
            return ResponseEntity.ok(match);
        }
        return ResponseEntity.ok(service.hasConfirmedReservation(passengerId, driverId, trajetId));
    }

    @PatchMapping("/internal/{id}/payment-initiated")
    @Operation(summary = "Internal: transition ACCEPTED → PAYMENT_PENDING (called by payment-service)")
    public ResponseEntity<Void> internalMarkPaymentInitiated(@PathVariable String id) {
        service.markPaymentInitiated(id);
        return ResponseEntity.noContent().build();
    }
}