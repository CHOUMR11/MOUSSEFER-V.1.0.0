package com.moussefer.station.controller;

import com.moussefer.station.dto.response.StationResponse;
import com.moussefer.station.entity.SecondaryPoint;
import com.moussefer.station.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
@RequiredArgsConstructor
@Tag(name = "Stations", description = "Interurban louage station management (public)")
public class StationController {

    private final StationService stationService;

    @GetMapping
    @Operation(summary = "Get all active stations")
    public ResponseEntity<List<StationResponse>> getAllActive() {
        return ResponseEntity.ok(
                stationService.getAllActive().stream().map(StationResponse::from).toList()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a station by ID")
    public ResponseEntity<StationResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(StationResponse.from(stationService.getById(id)));
    }

    @GetMapping("/city/{city}")
    @Operation(summary = "Get stations by city")
    public ResponseEntity<List<StationResponse>> getByCity(@PathVariable String city) {
        return ResponseEntity.ok(
                stationService.getByCity(city).stream().map(StationResponse::from).toList()
        );
    }

    @GetMapping("/{stationId}/secondary-points")
    @Operation(summary = "Get secondary departure points for a station")
    public ResponseEntity<List<SecondaryPoint>> getSecondaryPoints(@PathVariable String stationId) {
        return ResponseEntity.ok(stationService.getSecondaryPoints(stationId));
    }

    @GetMapping("/{stationId}/stats")
    @Operation(summary = "Get statistics for a specific station")
    public ResponseEntity<java.util.Map<String, Object>> getStationStats(@PathVariable String stationId) {
        return ResponseEntity.ok(stationService.getStationStats(stationId));
    }
}