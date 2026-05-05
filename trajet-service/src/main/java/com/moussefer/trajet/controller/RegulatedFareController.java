package com.moussefer.trajet.controller;

import com.moussefer.trajet.dto.request.RegulatedFareDto;
import com.moussefer.trajet.dto.response.FareImportReport;
import com.moussefer.trajet.entity.RegulatedFare;
import com.moussefer.trajet.service.RegulatedFareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Endpoints for government-regulated louage fares.
 *
 * Two access tiers:
 *   - Public (DRIVER or PASSENGER): lookup fare for a route, list all fares
 *   - Internal (admin-service only): create/update/delete/import — writes
 *     are routed via /internal/* and require the shared X-Internal-Secret
 *     header. The admin-service exposes a proxy at /api/v1/admin/fares.
 */
@RestController
@RequestMapping("/api/v1/fares")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Regulated Fares", description = "Government-set louage fares")
public class RegulatedFareController {

    private final RegulatedFareService service;

    // ────────────── Public read endpoints ──────────────

    @GetMapping
    @Operation(summary = "List regulated fares (public — drivers and passengers)")
    public ResponseEntity<Page<RegulatedFare>> list(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 50) Pageable pageable) {
        // For public calls, force active=true so drivers don't see disabled fares
        Boolean effectiveActive = active != null ? active : Boolean.TRUE;
        return ResponseEntity.ok(service.search(city, effectiveActive, pageable));
    }

    @GetMapping("/lookup")
    @Operation(summary = "Lookup the official fare for a specific route",
            description = "Returns 404 if no regulated fare exists for that route yet.")
    public ResponseEntity<Object> lookup(@RequestParam String departureCity,
                                         @RequestParam String arrivalCity) {
        return service.findActiveFare(departureCity, arrivalCity)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "No regulated fare found for this route",
                        "departureCity", departureCity,
                        "arrivalCity", arrivalCity
                )));
    }

    // ────────────── Internal admin endpoints ──────────────

    @GetMapping("/internal/admin")
    @Operation(summary = "Admin: list all fares (active and inactive)")
    public ResponseEntity<Page<RegulatedFare>> adminList(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(service.search(city, active, pageable));
    }

    @PostMapping("/internal/admin")
    @Operation(summary = "Admin: create or update a single regulated fare")
    public ResponseEntity<RegulatedFare> upsert(
            @RequestBody RegulatedFareDto dto,
            @RequestHeader(value = "X-Admin-Id", required = false) String adminId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upsert(dto, adminId));
    }

    @PatchMapping("/internal/admin/{id}/active")
    @Operation(summary = "Admin: toggle a fare's active status")
    public ResponseEntity<Void> setActive(
            @PathVariable String id,
            @RequestParam boolean active) {
        service.setActive(id, active);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/internal/admin/{id}")
    @Operation(summary = "Admin: delete a regulated fare")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/internal/admin/import",
            consumes = {"multipart/form-data", "application/octet-stream"})
    @Operation(summary = "Admin: bulk import fares from a JSON or CSV file")
    public ResponseEntity<FareImportReport> importFile(
            @RequestParam(value = "file") MultipartFile file,
            @RequestParam(value = "format", required = false) String format,
            @RequestHeader(value = "X-Admin-Id", required = false) String adminId) throws Exception {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String detectedFormat = format != null ? format.toUpperCase() : detectFormat(file);
        FareImportReport report;
        switch (detectedFormat) {
            case "JSON" -> report = service.importFromJson(file.getBytes(), adminId);
            case "CSV"  -> report = service.importFromCsv(file.getBytes(), adminId);
            default     -> {
                return ResponseEntity.badRequest().build();
            }
        }
        log.info("Admin {} imported {} fares ({}): created={} updated={} skipped={}",
                adminId, report.getTotal(), detectedFormat, report.getCreated(),
                report.getUpdated(), report.getSkipped());
        return ResponseEntity.ok(report);
    }

    @PostMapping(value = "/internal/admin/import-json",
            consumes = "application/json")
    @Operation(summary = "Admin: bulk import fares by posting the JSON content inline")
    public ResponseEntity<FareImportReport> importJsonInline(
            @RequestBody byte[] body,
            @RequestHeader(value = "X-Admin-Id", required = false) String adminId) {
        FareImportReport report = service.importFromJson(body, adminId);
        return ResponseEntity.ok(report);
    }

    private String detectFormat(MultipartFile file) {
        String name = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        if (name.endsWith(".json")) return "JSON";
        if (name.endsWith(".csv"))  return "CSV";
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        if (contentType.contains("json")) return "JSON";
        if (contentType.contains("csv"))  return "CSV";
        return "JSON"; // default
    }
}
