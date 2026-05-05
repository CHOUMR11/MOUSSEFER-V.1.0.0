package com.moussefer.demande.controller;

import com.moussefer.demande.dto.MergeDemandeResponse;
import com.moussefer.demande.entity.VehicleType;
import com.moussefer.demande.service.DemandeMergeService;
import com.moussefer.demande.service.DemandeService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/demandes/internal/admin")
@RequiredArgsConstructor
public class InternalAdminDemandeController {

    private final DemandeMergeService mergeService;
    private final DemandeService demandeService;

    @PostMapping("/merge")
    @Operation(summary = "Internal admin: fusionner toutes les demandes similaires")
    public ResponseEntity<List<MergeDemandeResponse>> mergeAll() {
        return ResponseEntity.ok(mergeService.mergeAll());
    }

    @PostMapping("/merge/route")
    @Operation(summary = "Internal admin: fusionner les demandes similaires pour une route donnée")
    public ResponseEntity<MergeDemandeResponse> mergeByRoute(
            @RequestParam String departureCity,
            @RequestParam String arrivalCity,
            @RequestParam String vehicleType,
            @RequestParam String requestedDate) {
        VehicleType vt = VehicleType.valueOf(vehicleType.toUpperCase());
        LocalDate date = LocalDate.parse(requestedDate);
        return ResponseEntity.ok(mergeService.mergeByRoute(departureCity, arrivalCity, vt, date));
    }

    @PostMapping("/demandes/{demandeId}/close")
    @Operation(summary = "Internal admin: fermer une demande")
    public ResponseEntity<Void> adminCloseDemande(@PathVariable String demandeId) {
        demandeService.adminCloseDemande(demandeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/demandes/{demandeId}/cancel")
    @Operation(summary = "Internal admin: annuler une demande")
    public ResponseEntity<Void> adminCancelDemande(@PathVariable String demandeId) {
        demandeService.adminCancelDemande(demandeId);
        return ResponseEntity.noContent().build();
    }
}