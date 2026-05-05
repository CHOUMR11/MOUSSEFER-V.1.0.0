package com.moussefer.voyage.controller;

import com.moussefer.voyage.dto.request.OrganizerManualBookingRequest;
import com.moussefer.voyage.entity.BookingSource;
import com.moussefer.voyage.entity.ReservationVoyage;
import com.moussefer.voyage.repository.ReservationVoyageRepository;
import com.moussefer.voyage.service.OrganizerDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * All endpoints backing the organizer dashboard (maquette: "Tunisia Tours" views).
 *
 * Sidebar mapping:
 *   Vue d'ensemble        → GET  /overview
 *   Réservation           → GET  /reservations      (+ filter bySource)
 *   Voyages organisés     → existing VoyageController /my
 *   Finances et factures  → GET  /finances
 *   Codes promo           → handled by payment-service promo endpoints
 *   Clients               → GET  /clients
 *   Statistiques          → GET  /statistics
 *   Ajouter réservation
 *     "Hors Moussefer"    → POST /manual-booking
 */
@RestController
@RequestMapping("/api/v1/voyages/organizer")
@RequiredArgsConstructor
@Tag(name = "Organizer Dashboard", description = "Dashboard data for voyage organizers")
public class OrganizerDashboardController {

    private final OrganizerDashboardService dashboardService;
    private final ReservationVoyageRepository reservationRepository;

    private void requireOrganizer(String role) {
        if (!"ORGANIZER".equalsIgnoreCase(role)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Only ORGANIZER role can access this dashboard");
        }
    }

    @GetMapping("/overview")
    @Operation(summary = "Dashboard overview (KPIs + quick actions)")
    public ResponseEntity<Map<String, Object>> overview(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role) {
        requireOrganizer(role);
        return ResponseEntity.ok(dashboardService.overview(organizerId));
    }

    @GetMapping("/finances")
    @Operation(summary = "Finances et factures — revenue KPIs, monthly chart, recent invoices")
    public ResponseEntity<Map<String, Object>> finances(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role) {
        requireOrganizer(role);
        return ResponseEntity.ok(dashboardService.finances(organizerId));
    }

    @GetMapping("/clients")
    @Operation(summary = "Clients — weekly reservations chart + top destinations")
    public ResponseEntity<Map<String, Object>> clients(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role) {
        requireOrganizer(role);
        return ResponseEntity.ok(dashboardService.clients(organizerId));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Statistiques — conversion rate, average revenue, booking source breakdown")
    public ResponseEntity<Map<String, Object>> statistics(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role) {
        requireOrganizer(role);
        return ResponseEntity.ok(dashboardService.statistics(organizerId));
    }

    @GetMapping("/reservations")
    @Operation(summary = "All reservations on my voyages (with optional source filter)",
            description = "bookingSource filter: PLATFORM, PHONE, AGENCY, DIRECT. " +
                    "Omit to see all. Matches the 'Réservation' tabs in the maquette " +
                    "(Tous, Moussefer, Hors Moussefer, En attente, Confirmés).")
    public ResponseEntity<Page<ReservationVoyage>> listReservations(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) BookingSource bookingSource,
            @PageableDefault(size = 20) Pageable pageable) {
        requireOrganizer(role);
        Page<ReservationVoyage> page = (bookingSource == null)
                ? reservationRepository.findByOrganizer(organizerId, pageable)
                : reservationRepository.findByOrganizerAndSource(organizerId, bookingSource, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/manual-booking")
    @Operation(summary = "Create a 'Hors Moussefer' manual reservation",
            description = "Used when a client booked via phone, at the agency, or through " +
                    "any direct channel. The passenger may not have a Moussefer account — " +
                    "the organizer records contact info manually.")
    public ResponseEntity<ReservationVoyage> manualBooking(
            @RequestHeader("X-User-Id") String organizerId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody OrganizerManualBookingRequest req) {
        requireOrganizer(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dashboardService.createManualBooking(organizerId, req));
    }
}
