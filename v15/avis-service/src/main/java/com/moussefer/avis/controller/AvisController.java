package com.moussefer.avis.controller;

import com.moussefer.avis.dto.AvisRequest;
import com.moussefer.avis.dto.AvisResponse;
import com.moussefer.avis.entity.Avis;
import com.moussefer.avis.service.AvisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/avis")
@RequiredArgsConstructor
@Tag(name = "Avis", description = "Driver ratings and reviews")
public class AvisController {

    private final AvisService service;

    @PostMapping
    @Operation(summary = "Submit a rating for a driver after a trip")
    public ResponseEntity<AvisResponse> submit(
            @RequestHeader("X-User-Id") String passengerId,
            @Valid @RequestBody AvisRequest request) {
        Avis avis = service.submit(
                passengerId,
                request.getDriverId(),
                request.getTrajetId(),
                request.getReservationId(),
                request.getRating(),
                request.getComment()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(AvisResponse.from(avis));
    }

    @GetMapping("/{avisId}")
    @Operation(summary = "Get a specific review by ID")
    public ResponseEntity<AvisResponse> getById(@PathVariable String avisId) {
        return ResponseEntity.ok(AvisResponse.from(service.getById(avisId)));
    }

    @PutMapping("/{avisId}")
    @Operation(summary = "Update own review (passenger only)")
    public ResponseEntity<AvisResponse> updateAvis(
            @PathVariable String avisId,
            @RequestHeader("X-User-Id") String passengerId,
            @Valid @RequestBody AvisRequest request) {
        Avis avis = service.updateAvis(passengerId, avisId, request.getRating(), request.getComment());
        return ResponseEntity.ok(AvisResponse.from(avis));
    }

    @GetMapping("/driver/{driverId}")
    @Operation(summary = "Get all reviews for a specific driver")
    public ResponseEntity<List<AvisResponse>> getForDriver(@PathVariable String driverId) {
        List<AvisResponse> responses = service.getForDriver(driverId).stream()
                .map(AvisResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/me")
    @Operation(summary = "Get all reviews for the authenticated driver")
    public ResponseEntity<List<AvisResponse>> getMyReviews(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role) {
        if (!"DRIVER".equalsIgnoreCase(role) && !role.toUpperCase().contains("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        List<AvisResponse> responses = service.getForDriver(userId).stream()
                .map(AvisResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/reservation/{reservationId}")
    @Operation(summary = "Get the review for a specific reservation (if exists)")
    public ResponseEntity<AvisResponse> getForReservation(@PathVariable String reservationId) {
        return service.getForReservation(reservationId)
                .map(avis -> ResponseEntity.ok(AvisResponse.from(avis)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Internal Admin Endpoint (called by admin-service) ====================
    @DeleteMapping("/internal/admin/{avisId}")
    @Operation(summary = "Internal admin: delete a review (MODERATOR or ADMIN only – called by admin-service)")
    public ResponseEntity<Void> internalAdminDeleteAvis(@PathVariable String avisId) {
        service.deleteAvis(avisId);
        return ResponseEntity.noContent().build();
    }
}