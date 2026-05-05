package com.moussefer.loyalty.controller;

import com.moussefer.loyalty.dto.response.LoyaltyAccountResponse;
import com.moussefer.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loyalty/internal/admin")
@RequiredArgsConstructor
public class InternalAdminLoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping("/account/{userId}")
    @Operation(summary = "Internal admin: get loyalty account by userId")
    public ResponseEntity<LoyaltyAccountResponse> getAccount(@PathVariable String userId) {
        return ResponseEntity.ok(LoyaltyAccountResponse.from(loyaltyService.getOrCreate(userId)));
    }

    @PostMapping("/earn/{userId}")
    @Operation(summary = "Internal admin: award trip points (called by reservation-service or manually)")
    public ResponseEntity<LoyaltyAccountResponse> earnTripPoints(
            @PathVariable String userId,
            @RequestParam String reservationId) {
        return ResponseEntity.ok(LoyaltyAccountResponse.from(
                loyaltyService.earnTripPoints(userId, reservationId)));
    }
}