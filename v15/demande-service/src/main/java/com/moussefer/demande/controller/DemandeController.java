package com.moussefer.demande.controller;

import com.moussefer.demande.dto.*;
import com.moussefer.demande.entity.DemandeCollective;
import com.moussefer.demande.service.DemandeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/demandes")
@RequiredArgsConstructor
@Tag(name = "Demandes collectives", description = "Création et participation à des voyages groupés")
public class DemandeController {

    private final DemandeService demandeService;

    @PostMapping
    @Operation(summary = "Créer une demande collective (organisateur)")
    public ResponseEntity<DemandeResponse> createDemande(
            @RequestHeader("X-User-Id") String organisateurId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody DemandeCreationRequest request) {
        if (!"ORGANIZER".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        DemandeCollective demande = demandeService.createDemande(organisateurId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DemandeResponse.from(demande));
    }

    @PostMapping("/join")
    @Operation(summary = "Rejoindre une demande collective existante (passager)")
    public ResponseEntity<DemandeResponse> joinDemande(
            @RequestHeader("X-User-Id") String passengerId,
            @Valid @RequestBody JoinRequest request) {
        DemandeCollective demande = demandeService.joinDemande(passengerId, request.getDemandeId(), request.getSeatsReserved());
        return ResponseEntity.ok(DemandeResponse.from(demande));
    }

    @GetMapping("/search")
    @Operation(summary = "Rechercher des demandes collectives ouvertes")
    public ResponseEntity<List<DemandeResponse>> searchDemandes(@Valid DemandeSearchRequest request) {
        List<DemandeCollective> demandes = demandeService.searchOpenDemandes(request);
        List<DemandeResponse> responses = demandes.stream()
                .map(DemandeResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{demandeId}")
    @Operation(summary = "Obtenir les détails d’une demande")
    public ResponseEntity<DemandeResponse> getDemande(@PathVariable String demandeId) {
        DemandeCollective demande = demandeService.getDemandeById(demandeId);
        return ResponseEntity.ok(DemandeResponse.from(demande));
    }

    @GetMapping("/my")
    @Operation(summary = "Lister les demandes créées par l’organisateur connecté")
    public ResponseEntity<List<DemandeResponse>> getMyDemandes(@RequestHeader("X-User-Id") String organisateurId) {
        List<DemandeCollective> demandes = demandeService.getDemandesByOrganisateur(organisateurId);
        List<DemandeResponse> responses = demandes.stream()
                .map(DemandeResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{demandeId}/close")
    @Operation(summary = "Fermer une demande (organisateur uniquement)")
    public ResponseEntity<Void> closeDemande(
            @PathVariable String demandeId,
            @RequestHeader("X-User-Id") String organisateurId) {
        demandeService.closeDemande(organisateurId, demandeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{demandeId}/convert")
    @Operation(summary = "Convertir une demande déclenchée en trajet (chauffeur)")
    public ResponseEntity<Void> convertDemandeToTrajet(
            @PathVariable String demandeId,
            @RequestHeader("X-User-Id") String driverId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody(required = false) ConvertDemandeRequest request) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (request == null) request = new ConvertDemandeRequest();
        demandeService.convertDemandeToTrajet(driverId, demandeId, request);
        return ResponseEntity.noContent().build();
    }

    // ==================== Internal endpoints (inter‑service) ====================
    @GetMapping("/internal/waiting-passengers")
    @Operation(summary = "Get passenger IDs waiting for a route (internal use for notification-service)")
    public ResponseEntity<List<String>> getWaitingPassengers(
            @RequestParam String departureCity,
            @RequestParam String arrivalCity) {
        List<String> passengerIds = demandeService.getWaitingPassengersByRoute(departureCity, arrivalCity);
        return ResponseEntity.ok(passengerIds);
    }
}