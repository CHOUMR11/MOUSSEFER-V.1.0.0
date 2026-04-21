package com.moussefer.banner.controller;

import com.moussefer.banner.dto.request.BannerRequest;
import com.moussefer.banner.dto.response.BannerResponse;
import com.moussefer.banner.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/banners/internal/admin")
@RequiredArgsConstructor
public class InternalAdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    @Operation(summary = "Internal admin: get all banners")
    public ResponseEntity<List<BannerResponse>> getAll() {
        return ResponseEntity.ok(
                bannerService.getAll().stream().map(BannerResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Internal admin: get banner by ID")
    public ResponseEntity<BannerResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(BannerResponse.from(bannerService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Internal admin: create a banner")
    public ResponseEntity<BannerResponse> create(@Valid @RequestBody BannerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BannerResponse.from(bannerService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Internal admin: update a banner")
    public ResponseEntity<BannerResponse> update(@PathVariable String id,
                                                 @Valid @RequestBody BannerRequest request) {
        return ResponseEntity.ok(BannerResponse.from(bannerService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Internal admin: delete a banner")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        bannerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Internal admin: get banner performance stats")
    public ResponseEntity<Map<String, Object>> getBannerStats(@PathVariable String id) {
        return ResponseEntity.ok(bannerService.getBannerPerformance(id));
    }

    @GetMapping("/performance")
    @Operation(summary = "Internal admin: get global banner performance report")
    public ResponseEntity<List<Map<String, Object>>> getGlobalPerformance() {
        return ResponseEntity.ok(bannerService.getGlobalPerformance());
    }
}