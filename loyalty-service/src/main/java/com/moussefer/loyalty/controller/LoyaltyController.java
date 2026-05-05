package com.moussefer.loyalty.controller;

import com.moussefer.loyalty.dto.request.RedeemRequest;
import com.moussefer.loyalty.dto.response.LoyaltyAccountResponse;
import com.moussefer.loyalty.dto.response.PointTransactionResponse;
import com.moussefer.loyalty.service.LoyaltyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loyalty")
@RequiredArgsConstructor
@Tag(name = "Loyalty", description = "Loyalty points and rewards (user facing)")
public class LoyaltyController {

    private final LoyaltyService loyaltyService;

    @GetMapping("/me")
    @Operation(summary = "Get current user's loyalty account")
    public ResponseEntity<LoyaltyAccountResponse> getMyAccount(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(LoyaltyAccountResponse.from(loyaltyService.getOrCreate(userId)));
    }

    @PostMapping("/redeem")
    @Operation(summary = "Redeem loyalty points")
    public ResponseEntity<LoyaltyAccountResponse> redeem(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody RedeemRequest request) {
        return ResponseEntity.ok(LoyaltyAccountResponse.from(
                loyaltyService.redeemPoints(userId, request.getPoints(), request.getReferenceId())));
    }

    @GetMapping("/history")
    @Operation(summary = "Get my points transaction history")
    public ResponseEntity<Page<PointTransactionResponse>> getHistory(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                loyaltyService.getHistory(userId, pageable).map(PointTransactionResponse::from));
    }
}