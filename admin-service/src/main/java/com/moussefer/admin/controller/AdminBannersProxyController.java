package com.moussefer.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Proxy controller — exposes /api/v1/admin/banners expected by the frontend,
 * delegates to banner-service's /api/v1/banners endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/banners")
@RequiredArgsConstructor
@Tag(name = "Admin – Banners", description = "Frontend-facing proxy to banner-service")
public class AdminBannersProxyController {

    private final WebClient bannerServiceWebClient;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @GetMapping
    @Operation(summary = "List all banners (admin view)")
    public ResponseEntity<Object> list() {
        Object body = bannerServiceWebClient.get().uri("/api/v1/banners/internal/admin")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        Object body = bannerServiceWebClient.get().uri("/api/v1/banners/internal/admin/" + id)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @PostMapping
    public ResponseEntity<Object> create(@RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = bannerServiceWebClient.post().uri("/api/v1/banners/internal/admin")
                .header("X-User-Id", adminId).bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id,
                                         @RequestBody Map<String, Object> body,
                                         @RequestHeader("X-User-Id") String adminId) {
        Object res = bannerServiceWebClient.put().uri("/api/v1/banners/internal/admin/" + id)
                .header("X-User-Id", adminId).bodyValue(body)
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,
                                       @RequestHeader("X-User-Id") String adminId) {
        bannerServiceWebClient.delete().uri("/api/v1/banners/internal/admin/" + id)
                .header("X-User-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(TIMEOUT);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Object> stats(@PathVariable String id) {
        Object body = bannerServiceWebClient.get().uri("/api/v1/banners/internal/admin/" + id + "/stats")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/performance")
    public ResponseEntity<Object> performance() {
        Object body = bannerServiceWebClient.get().uri("/api/v1/banners/internal/admin/performance")
                .retrieve().bodyToMono(Object.class).block(TIMEOUT);
        return ResponseEntity.ok(body);
    }
}
