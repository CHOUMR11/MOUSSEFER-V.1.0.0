package com.moussefer.station.controller;

import com.moussefer.station.dto.request.StationRequest;
import com.moussefer.station.dto.request.SecondaryPointOrderRequest;
import com.moussefer.station.dto.response.StationResponse;
import com.moussefer.station.entity.SecondaryPoint;
import com.moussefer.station.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/stations/internal/admin")
@RequiredArgsConstructor
public class InternalAdminStationController {

    private final StationService stationService;

    @PostMapping
    @Operation(summary = "Internal admin: create a new station")
    public ResponseEntity<StationResponse> create(@Valid @RequestBody StationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StationResponse.from(stationService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Internal admin: update a station")
    public ResponseEntity<StationResponse> update(@PathVariable String id,
                                                  @Valid @RequestBody StationRequest request) {
        return ResponseEntity.ok(StationResponse.from(stationService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Internal admin: deactivate a station")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        stationService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{stationId}/secondary-points")
    @Operation(summary = "Internal admin: add a secondary point")
    public ResponseEntity<SecondaryPoint> addSecondaryPoint(
            @PathVariable String stationId,
            @RequestParam String name,
            @RequestParam(required = false) String address,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "0") int displayOrder) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stationService.addSecondaryPoint(stationId, name, address, latitude, longitude, displayOrder));
    }

    @DeleteMapping("/secondary-points/{pointId}")
    @Operation(summary = "Internal admin: remove a secondary point")
    public ResponseEntity<Void> removeSecondaryPoint(@PathVariable String pointId) {
        stationService.removeSecondaryPoint(pointId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/secondary-points/{pointId}")
    @Operation(summary = "Internal admin: update secondary point display order")
    public ResponseEntity<SecondaryPoint> updateSecondaryPointOrder(
            @PathVariable String pointId,
            @Valid @RequestBody SecondaryPointOrderRequest request) {
        return ResponseEntity.ok(stationService.updateSecondaryPointOrder(pointId, request.getDisplayOrder()));
    }

    @GetMapping("/stats")
    @Operation(summary = "Internal admin: get global station statistics")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        return ResponseEntity.ok(stationService.getGlobalStats());
    }
}