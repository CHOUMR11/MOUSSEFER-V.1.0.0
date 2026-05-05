package com.moussefer.trajet.controller;

import com.moussefer.trajet.dto.external.TrajetInfoResponse;
import com.moussefer.trajet.dto.request.CreateTrajetRequest;
import com.moussefer.trajet.dto.request.SearchTrajetRequest;
import com.moussefer.trajet.dto.request.UpdateTrajetRequest;
import com.moussefer.trajet.dto.response.TrajetResponse;
import com.moussefer.trajet.service.TrajetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trajets")
@RequiredArgsConstructor
@Tag(name = "Trajets", description = "Louage departure management")
public class TrajetController {

    private final TrajetService trajetService;

    // ==================== Public / Driver Endpoints ====================
    @PostMapping
    @Operation(summary = "Driver: publish a departure")
    public ResponseEntity<TrajetResponse> publish(@RequestHeader("X-User-Id") String driverId,
                                                  @Valid @RequestBody CreateTrajetRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(trajetService.publishTrajet(driverId, req));
    }

    @GetMapping("/search")
    @Operation(summary = "Passenger: search available trajets")
    public ResponseEntity<List<TrajetResponse>> search(@Valid SearchTrajetRequest req) {
        return ResponseEntity.ok(trajetService.search(req));
    }

    @GetMapping("/my")
    @Operation(summary = "Driver: list own trajets")
    public ResponseEntity<List<TrajetResponse>> getMyTrajets(@RequestHeader("X-User-Id") String driverId) {
        return ResponseEntity.ok(trajetService.getMyTrajets(driverId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get trajet details")
    public ResponseEntity<TrajetResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(trajetService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Driver: update trajet details")
    public ResponseEntity<TrajetResponse> updateTrajet(@RequestHeader("X-User-Id") String driverId,
                                                       @PathVariable String id,
                                                       @Valid @RequestBody UpdateTrajetRequest req) {
        return ResponseEntity.ok(trajetService.updateTrajet(driverId, id, req));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Driver: cancel own trajet")
    public ResponseEntity<Void> cancelTrajet(@RequestHeader("X-User-Id") String driverId,
                                             @PathVariable String id) {
        trajetService.driverCancelTrajet(driverId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/depart")
    @Operation(summary = "Driver: mark trajet as departed")
    public ResponseEntity<TrajetResponse> markDeparted(@RequestHeader("X-User-Id") String driverId,
                                                       @PathVariable String id) {
        return ResponseEntity.ok(trajetService.markDeparted(driverId, id));
    }

    @PatchMapping("/{id}/reduce-seats")
    @Operation(summary = "Driver: manually reduce seats (offline booking)")
    public ResponseEntity<TrajetResponse> reduceSeats(@RequestHeader("X-User-Id") String driverId,
                                                      @PathVariable String id,
                                                      @RequestParam int seats) {
        return ResponseEntity.ok(trajetService.reduceSeats(driverId, id, seats));
    }

    // ==================== Internal Endpoints (inter‑service) ====================
    @GetMapping("/internal/{id}")
    @Operation(summary = "Internal: get trajet info for reservation-service")
    public ResponseEntity<TrajetInfoResponse> getTrajetInfoInternal(@PathVariable String id) {
        return ResponseEntity.ok(trajetService.getTrajetInfoForInternal(id));
    }

    @PatchMapping("/internal/{id}/reserve-temp")
    @Operation(summary = "Internal: temporarily reserve seats")
    public ResponseEntity<Void> reserveSeatsTemporarily(@PathVariable String id,
                                                        @RequestParam int seats) {
        trajetService.reserveSeatsTemporarily(id, seats);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/internal/{id}/confirm-seats")
    @Operation(summary = "Internal: confirm temporary reservation (after payment)")
    public ResponseEntity<Void> confirmReservationSeats(@PathVariable String id,
                                                        @RequestParam int seats) {
        trajetService.confirmReservationSeats(id, seats);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/internal/{id}/release-temp")
    @Operation(summary = "Internal: release temporary reservation")
    public ResponseEntity<Void> releaseTemporaryReservation(@PathVariable String id,
                                                            @RequestParam int seats) {
        trajetService.releaseTemporaryReservation(id, seats);
        return ResponseEntity.noContent().build();
    }

    // (Legacy internal endpoints kept for backward compatibility)
    @PatchMapping("/internal/{id}/reduce-seats")
    @Operation(summary = "Internal: reduce seats (legacy)")
    public ResponseEntity<Void> internalReduceSeats(@PathVariable String id,
                                                    @RequestParam int seats) {
        trajetService.internalReduceSeats(id, seats);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/internal/{id}/increment-seats")
    @Operation(summary = "Internal: restore seats (legacy)")
    public ResponseEntity<Void> internalIncrementSeats(@PathVariable String id,
                                                       @RequestParam int seats) {
        trajetService.internalIncrementSeats(id, seats);
        return ResponseEntity.noContent().build();
    }

    // ==================== Driver: seat count adjustment ====================
    //
    // Note métier : Moussefer ne gère PAS la vente au guichet. Les guichets
    // physiques de stations ont leur propre système et leurs propres agents.
    // La plateforme se concentre sur la réservation en ligne.
    //
    // Cependant, le chauffeur peut avoir besoin de corriger le compteur de
    // places restantes pour refléter la réalité du véhicule :
    //   - un passager hors-plateforme est monté à un point intermédiaire
    //   - un passager est descendu en cours de route et a libéré sa place
    //   - une erreur de comptage doit être rectifiée
    //
    // Cette méthode permet exactement ce cas d'usage et rien d'autre. Aucune
    // réservation n'est créée ou annulée — c'est juste une mise à jour du
    // compteur visible par les futurs passagers en ligne.

    @PatchMapping("/{id}/driver/update-seats")
    @Operation(summary = "Driver: corriger le nombre de places disponibles",
            description = "Met à jour le compteur availableSeats pour refléter la réalité physique du véhicule. " +
                    "La valeur est bornée serveur-side entre 0 et (totalSeats − reservedSeats) pour ne jamais " +
                    "écraser des réservations confirmées. Aucune réservation n'est affectée — c'est une simple " +
                    "correction du compteur affiché aux passagers.")
    public ResponseEntity<TrajetResponse> driverUpdateAvailableSeats(
            @RequestHeader("X-User-Id") String driverId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable String id,
            @RequestParam int availableSeats) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(trajetService.driverUpdateAvailableSeats(driverId, id, availableSeats));
    }

    // ==================== Internal Admin Endpoints (called by admin-service) ====================
    @DeleteMapping("/internal/admin/{trajetId}")
    @Operation(summary = "Internal admin: cancel any trajet")
    public ResponseEntity<Void> internalAdminCancelTrajet(@PathVariable String trajetId) {
        // adminId is not needed because audit logging is done by admin-service
        trajetService.adminCancelTrajet(trajetId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/internal/admin/{trajetId}/assign-driver")
    @Operation(summary = "Internal admin: assign or replace driver on a trajet")
    public ResponseEntity<TrajetResponse> internalAdminAssignDriver(@PathVariable String trajetId,
                                                                    @RequestParam String newDriverId) {
        return ResponseEntity.ok(trajetService.adminAssignDriver(trajetId, newDriverId));
    }

    @GetMapping("/internal/admin/all")
    @Operation(summary = "Internal admin: list all trajets with filters")
    public ResponseEntity<org.springframework.data.domain.Page<TrajetResponse>> internalAdminListAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String departureCity,
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(trajetService.adminListAll(status, driverId, departureCity, pageable));
    }
}