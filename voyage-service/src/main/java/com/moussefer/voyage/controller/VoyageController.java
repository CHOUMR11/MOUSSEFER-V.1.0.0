package com.moussefer.voyage.controller;

import com.moussefer.voyage.dto.request.AcceptReservationRequest;
import com.moussefer.voyage.dto.request.CreateVoyageRequest;
import com.moussefer.voyage.dto.request.ReserveVoyageRequest;
import com.moussefer.voyage.dto.request.UpdateVoyageRequest;
import com.moussefer.voyage.dto.response.PaymentInitiationResponse;
import com.moussefer.voyage.dto.response.ReservationVoyageResponse;
import com.moussefer.voyage.dto.response.VoyageResponse;
import com.moussefer.voyage.entity.ReservationVoyage;
import com.moussefer.voyage.entity.ReservationVoyageStatus;
import com.moussefer.voyage.exception.BusinessException;
import com.moussefer.voyage.exception.ResourceNotFoundException;
import com.moussefer.voyage.repository.ReservationVoyageRepository;
import com.moussefer.voyage.service.PaymentService;
import com.moussefer.voyage.service.VoyageImageService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/voyages")
@RequiredArgsConstructor
@Tag(name = "Voyages", description = "Organized trips management")
public class VoyageController {

    private final VoyageService voyageService;
    private final PaymentService paymentService;                     // << added
    private final ReservationVoyageRepository reservationRepository; // << added
    private final VoyageImageService voyageImageService;             // << V23 — image upload

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

    /**
     * V23 — Cover image upload for a voyage.
     *
     * Frontend (voyage.service.ts) calls this with multipart/form-data,
     * field name "file". The endpoint validates ownership (organizer must
     * own the voyage), uploads to MinIO bucket "voyage-images", persists
     * the URL on the voyage row, and returns { imageUrl }.
     *
     * Security: only ORGANIZER role may upload, and only to their own voyages.
     */
    @PostMapping(value = "/{id}/image", consumes = "multipart/form-data")
    @Operation(summary = "Organizer: upload cover image for a voyage",
            description = "Multipart upload (max 10MB, JPEG/PNG/WebP). Returns { imageUrl }.")
    public ResponseEntity<Map<String, String>> uploadVoyageImage(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id,
            @RequestParam("file") MultipartFile file) {
        if (role == null || !"ORGANIZER".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String imageUrl = voyageImageService.uploadVoyageImage(organizerId, id, file);
        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
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

    /**
     * NEW: Passenger initiates Stripe payment after organizer accepted the reservation.
     * Returns the Stripe clientSecret so the frontend confirms the payment on the client side.
     */
    @PostMapping("/reservations/{reservationId}/pay")
    @Operation(summary = "Passenger: initiate payment for an accepted voyage reservation")
    public ResponseEntity<PaymentInitiationResponse> payForReservation(
            @RequestHeader("X-User-Id") String passengerId,
            @PathVariable String reservationId) {
        ReservationVoyage reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        if (!passengerId.equals(reservation.getPassengerId())) {
            throw new BusinessException("Not your reservation");
        }
        if (reservation.getStatus() != ReservationVoyageStatus.PENDING_PAYMENT) {
            throw new BusinessException("Reservation is not awaiting payment");
        }
        PaymentInitiationResponse response = paymentService.initiatePayment(reservationId);
        return ResponseEntity.ok(response);
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
    @Operation(summary = "Organizer: accept a reservation and set it to PENDING_PAYMENT")
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

    // ==================== Internal Admin Endpoint ====================
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