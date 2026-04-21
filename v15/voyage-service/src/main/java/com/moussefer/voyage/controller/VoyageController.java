package com.moussefer.voyage.controller;

import com.moussefer.voyage.dto.request.AcceptReservationRequest;
import com.moussefer.voyage.dto.request.CreateVoyageRequest;
import com.moussefer.voyage.dto.request.ReserveVoyageRequest;
import com.moussefer.voyage.dto.request.UpdateVoyageRequest;
import com.moussefer.voyage.dto.response.ReservationVoyageResponse;
import com.moussefer.voyage.dto.response.VoyageResponse;
import com.moussefer.voyage.service.VoyageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/voyages")
@RequiredArgsConstructor
@Tag(name = "Voyages", description = "Organized trips management")
public class VoyageController {

    private final VoyageService voyageService;

    // ==================== Public Listing Endpoint ====================
    @GetMapping
    @Operation(summary = "List all available voyages (public, paginated)")
    public ResponseEntity<Page<VoyageResponse>> getAllVoyages(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(voyageService.findAllAvailable(pageable));
    }

    // ==================== Organizer Endpoints ====================
    @PostMapping
    @Operation(summary = "Organizer: create a new voyage")
    public ResponseEntity<VoyageResponse> createVoyage(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CreateVoyageRequest request) {
        if (!"ORGANIZER".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(voyageService.createVoyage(organizerId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Organizer: update an existing voyage (OPEN status only)")
    public ResponseEntity<VoyageResponse> updateVoyage(
            @PathVariable String id,
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody UpdateVoyageRequest request) {
        if (!"ORGANIZER".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(voyageService.updateVoyage(organizerId, id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Organizer: cancel own voyage (OPEN or FULL status only)")
    public ResponseEntity<Void> cancelVoyage(
            @RequestHeader("X-User-Id") String organizerId,
            @PathVariable String id) {
        voyageService.organizerCancelVoyage(organizerId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/my")
    @Operation(summary = "Organizer: get voyages created by me (paginated)")
    public ResponseEntity<Page<VoyageResponse>> getMyVoyages(
            @RequestHeader("X-User-Id") String organizerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(voyageService.getMyVoyages(organizerId, pageable));
    }

    @GetMapping("/organizer/reservations/{voyageId}")
    @Operation(summary = "Organizer: list reservations for a voyage (paginated)")
    public ResponseEntity<Page<ReservationVoyageResponse>> getReservationsForOrganizer(
            @RequestHeader("X-User-Id") String organizerId,
            @PathVariable String voyageId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(voyageService.getReservationsForOrganizer(organizerId, voyageId, pageable));
    }

    // ==================== Passenger Endpoints ====================
    @PostMapping("/reserve")
    @Operation(summary = "Passenger: reserve seats (pending organizer approval)")
    public ResponseEntity<ReservationVoyageResponse> reserveSeats(
            @RequestHeader("X-User-Id") String passengerId,
            @Valid @RequestBody ReserveVoyageRequest request) {
        return ResponseEntity.ok(voyageService.reserveSeats(passengerId, request));
    }

    @GetMapping("/my-reservations")
    @Operation(summary = "Passenger: list my reservations (paginated)")
    public ResponseEntity<Page<ReservationVoyageResponse>> getMyReservations(
            @RequestHeader("X-User-Id") String passengerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(voyageService.getMyReservations(passengerId, pageable));
    }

    @GetMapping("/reservations/{reservationId}")
    @Operation(summary = "Get a specific voyage reservation by ID (passenger or organizer)")
    public ResponseEntity<ReservationVoyageResponse> getReservationById(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String reservationId) {
        return ResponseEntity.ok(voyageService.getReservationById(userId, reservationId));
    }

    // ==================== Common Endpoints ====================
    @GetMapping("/search")
    @Operation(summary = "Search available voyages with filters")
    public ResponseEntity<Page<VoyageResponse>> searchVoyages(
            @RequestParam String departureCity,
            @RequestParam String arrivalCity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String organizerId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(voyageService.searchVoyages(departureCity, arrivalCity, date,
                organizerId, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get voyage details")
    public ResponseEntity<VoyageResponse> getVoyage(@PathVariable String id) {
        return ResponseEntity.ok(voyageService.getVoyage(id));
    }

    // ==================== Organizer Acceptance/Refusal ====================
    @PostMapping("/reservations/accept")
    @Operation(summary = "Organizer: accept a reservation and initiate payment")
    public ResponseEntity<ReservationVoyageResponse> acceptReservation(
            @RequestHeader("X-User-Id") String organizerId,
            @Valid @RequestBody AcceptReservationRequest request) {
        return ResponseEntity.ok(voyageService.acceptReservation(organizerId, request.getReservationId()));
    }

    @PostMapping("/reservations/{reservationId}/refuse")
    @Operation(summary = "Organizer: refuse a reservation")
    public ResponseEntity<Void> refuseReservation(
            @RequestHeader("X-User-Id") String organizerId,
            @PathVariable String reservationId,
            @RequestParam(required = false) String reason) {
        voyageService.refuseReservation(organizerId, reservationId, reason);
        return ResponseEntity.noContent().build();
    }

    // ==================== Internal Admin Endpoint (called by admin-service) ====================
    @DeleteMapping("/internal/admin/{voyageId}")
    @Operation(summary = "Internal admin: cancel any voyage")
    public ResponseEntity<Void> internalAdminCancelVoyage(@PathVariable String voyageId) {
        voyageService.adminCancelVoyage(voyageId);
        return ResponseEntity.noContent().build();
    }

    // ==================== Stripe Webhook ====================
    @PostMapping(value = "/webhook", consumes = "application/json")
    @Operation(summary = "Stripe webhook for voyage payment confirmation (public endpoint, signature-verified)")
    public ResponseEntity<String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        try {
            voyageService.handleStripeWebhook(payload, sigHeader);
            return ResponseEntity.ok("received");
        } catch (Exception e) {
            return ResponseEntity.status(400).body("Webhook error: " + e.getMessage());
        }
    }
}