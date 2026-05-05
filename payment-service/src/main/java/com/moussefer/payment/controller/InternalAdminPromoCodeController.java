package com.moussefer.payment.controller;

import com.moussefer.payment.dto.request.CreatePromoCodeRequest;
import com.moussefer.payment.dto.request.UpdatePromoCodeRequest;
import com.moussefer.payment.dto.response.PromoCodeResponse;
import com.moussefer.payment.dto.response.PromoCodeStatsResponse;
import com.moussefer.payment.service.AdminPromoCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/internal/admin/promo-codes")
@RequiredArgsConstructor
public class InternalAdminPromoCodeController {

    private final AdminPromoCodeService promoService;

    @GetMapping
    public ResponseEntity<Page<PromoCodeResponse>> list(
            @RequestParam(defaultValue = "all") String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return switch (status.toLowerCase()) {
            case "active"  -> ResponseEntity.ok(promoService.listActive(pageable));
            case "expired" -> ResponseEntity.ok(promoService.listExpiredOrExhausted(pageable));
            default        -> ResponseEntity.ok(promoService.listAll(pageable));
        };
    }

    @GetMapping("/stats")
    public ResponseEntity<PromoCodeStatsResponse> stats() {
        return ResponseEntity.ok(promoService.getStats());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromoCodeResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(promoService.getById(id));
    }

    @PostMapping
    public ResponseEntity<PromoCodeResponse> create(
            @RequestHeader("X-Admin-Id") String adminId,
            @Valid @RequestBody CreatePromoCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(promoService.create(adminId, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PromoCodeResponse> update(
            @PathVariable String id,
            @RequestHeader("X-Admin-Id") String adminId,
            @Valid @RequestBody UpdatePromoCodeRequest request) {
        return ResponseEntity.ok(promoService.update(adminId, id, request));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<PromoCodeResponse> activate(
            @PathVariable String id,
            @RequestHeader("X-Admin-Id") String adminId) {
        return ResponseEntity.ok(promoService.setActive(adminId, id, true));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<PromoCodeResponse> deactivate(
            @PathVariable String id,
            @RequestHeader("X-Admin-Id") String adminId) {
        return ResponseEntity.ok(promoService.setActive(adminId, id, false));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            @RequestHeader("X-Admin-Id") String adminId) {
        promoService.delete(adminId, id);
        return ResponseEntity.noContent().build();
    }
}