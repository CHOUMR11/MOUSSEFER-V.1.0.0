package com.moussefer.user.controller;

import com.moussefer.user.entity.VerificationStatus;
import com.moussefer.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * FIX #12: Corrected method calls to match actual UserService signatures.
 *
 * Bugs fixed:
 *  - setSuspension(userId, until, reason) → uses userService.setSuspension(userId, until, reason)
 *  - liftSuspension(userId)               → uses userService.liftSuspension(userId)
 *  - updateVerificationStatus(userId, s, r) → uses userService.updateVerificationStatus(userId, status, reason)
 *
 * Protected by InternalAuthFilter (X-Internal-Secret header).
 */
@RestController
@RequestMapping("/api/v1/users/internal/admin")
@RequiredArgsConstructor
public class InternalAdminProxyController {

    private final UserService userService;

    @GetMapping("/{userId}/suspension")
    @Operation(summary = "Get suspension status of a user")
    public ResponseEntity<Map<String, Object>> getSuspension(@PathVariable String userId) {
        LocalDateTime until = userService.getSuspensionEndDate(userId);
        boolean suspended = until != null && LocalDateTime.now().isBefore(until);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "suspended", suspended,
                "suspendedUntil", until != null ? until.toString() : ""
        ));
    }

    @PostMapping("/{userId}/suspension")
    @Operation(summary = "Suspend a user (called by admin-service proxy)")
    public ResponseEntity<Void> setSuspension(
            @PathVariable String userId,
            @RequestBody Map<String, Object> body) {

        String until = (String) body.get("until");
        String reason = (String) body.getOrDefault("reason", "Suspended via admin-service");

        if (until != null && !until.isBlank()) {
            LocalDateTime untilDate = LocalDateTime.parse(until);
            // setSuspension already checks if untilDate is in the future internally
            userService.setSuspension(userId, untilDate, reason);
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/suspension")
    @Operation(summary = "Lift suspension (called by admin-service proxy)")
    public ResponseEntity<Void> liftSuspension(@PathVariable String userId) {
        userService.liftSuspension(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/verification")
    @Operation(summary = "Update KYC verification status (called by admin-service proxy)")
    public ResponseEntity<Void> updateVerification(
            @PathVariable String userId,
            @RequestBody Map<String, String> body) {

        VerificationStatus status = VerificationStatus.valueOf(body.get("status").toUpperCase());
        String rejectionReason = body.get("rejectionReason");

        userService.updateVerificationStatus(userId, status, rejectionReason);
        return ResponseEntity.ok().build();
    }
}