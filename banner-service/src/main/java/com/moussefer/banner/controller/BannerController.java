package com.moussefer.banner.controller;

import com.moussefer.banner.dto.response.BannerResponse;
import com.moussefer.banner.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
@Tag(name = "Banners", description = "Public promotional banners")
public class BannerController {

    private final BannerService bannerService;

    @GetMapping("/active")
    @Operation(summary = "Get active banners for current audience (public)")
    public ResponseEntity<List<BannerResponse>> getActive(
            @RequestParam(required = false) String audience) {
        return ResponseEntity.ok(
                bannerService.getActiveBanners(audience).stream().map(BannerResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get banner by ID (public)")
    public ResponseEntity<BannerResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(BannerResponse.from(bannerService.getById(id)));
    }

    @PostMapping("/{id}/impression")
    @Operation(summary = "Track a banner impression")
    public ResponseEntity<Void> trackImpression(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        bannerService.trackInteraction(id, userId, "IMPRESSION");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/click")
    @Operation(summary = "Track a banner click")
    public ResponseEntity<Void> trackClick(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        bannerService.trackInteraction(id, userId, "CLICK");
        return ResponseEntity.noContent().build();
    }
}