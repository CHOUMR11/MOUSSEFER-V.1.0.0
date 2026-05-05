package com.moussefer.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Admin-facing proxy for government-regulated fare management.
 *
 * Frontend calls hit /api/v1/admin/fares/**; this controller proxies to
 * trajet-service's /api/v1/fares/internal/admin/** endpoints with the
 * shared internal secret. The frontend admin UI never needs to know the
 * target microservice — it only sees the /admin/ prefix.
 *
 * Authorization:
 *   - SUPER_ADMIN: all operations
 *   - FINANCIAL_ADMIN: all operations (fares are financial data)
 *   - Others: denied by AdminRoleGuard at the filter layer
 */
@RestController
@RequestMapping("/api/v1/admin/fares")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin – Regulated Fares", description = "Manage official Ministry of Transport louage tariffs")
public class AdminFaresProxyController {

    private final WebClient trajetServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(15); // imports may be slower

    @GetMapping
    @Operation(summary = "List regulated fares (admin view — includes inactive)")
    public ResponseEntity<Object> list(@RequestParam(required = false) String city,
                                       @RequestParam(required = false) Boolean active,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        StringBuilder uri = new StringBuilder("/api/v1/fares/internal/admin?page=")
                .append(page).append("&size=").append(size);
        if (city != null)   uri.append("&city=").append(city);
        if (active != null) uri.append("&active=").append(active);
        Object body = trajetServiceWebClient.get().uri(uri.toString())
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @PostMapping
    @Operation(summary = "Create or update a single regulated fare")
    public ResponseEntity<Object> upsert(@RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = trajetServiceWebClient.post()
                .uri("/api/v1/fares/internal/admin")
                .header("X-Admin-Id", adminId)
                .bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.status(201).body(res);
    }

    @PatchMapping("/{id}/active")
    @Operation(summary = "Enable or disable a regulated fare")
    public ResponseEntity<Void> setActive(@PathVariable String id,
                                          @RequestParam boolean active) {
        trajetServiceWebClient.patch()
                .uri("/api/v1/fares/internal/admin/" + id + "/active?active=" + active)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a regulated fare")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        trajetServiceWebClient.delete()
                .uri("/api/v1/fares/internal/admin/" + id)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk import fares from a JSON or CSV file")
    public ResponseEntity<Object> importFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", required = false) String format,
            @RequestHeader("X-User-Id") String adminId) throws Exception {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is required"));
        }

        MultipartBodyBuilder mbb = new MultipartBodyBuilder();
        mbb.part("file", new ByteArrayResource(file.getBytes()) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
        }).contentType(MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
        if (format != null) mbb.part("format", format);

        Object res = trajetServiceWebClient.post()
                .uri("/api/v1/fares/internal/admin/import")
                .header("X-Admin-Id", adminId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(mbb.build()))
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        log.info("Admin {} imported fares file {} ({})", adminId, file.getOriginalFilename(), format);
        return ResponseEntity.ok(res);
    }

    @PostMapping(value = "/import-json", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Bulk import by posting the JSON content inline",
            description = "Convenience endpoint for admins who paste the JSON body directly " +
                    "into Postman or a form — avoids multipart boilerplate.")
    public ResponseEntity<Object> importJsonInline(
            @RequestBody String jsonBody,
            @RequestHeader("X-User-Id") String adminId) {
        Object res = trajetServiceWebClient.post()
                .uri("/api/v1/fares/internal/admin/import-json")
                .header("X-Admin-Id", adminId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }
}
