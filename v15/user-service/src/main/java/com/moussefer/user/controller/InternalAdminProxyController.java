package com.moussefer.user.controller;

import com.moussefer.user.entity.VerificationStatus;
import com.moussefer.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/internal/admin")
@RequiredArgsConstructor
public class InternalAdminProxyController {

    private final UserService userService;

    @GetMapping("/{userId}/suspension")
    @Operation(summary = "Get suspension end date")
    public ResponseEntity<Map<String, Object>> getSuspension(@PathVariable String userId) {
        LocalDateTime until = userService.getSuspensionEndDate(userId);
        return ResponseEntity.ok(Map.of("suspendedUntil", until));
    }

    @PostMapping("/{userId}/suspension")
    @Operation(summary = "Set suspension (called by admin-service)")
    public ResponseEntity<Void> setSuspension(@PathVariable String userId,
                                              @RequestBody Map<String, Object> body) {
        LocalDateTime until = LocalDateTime.parse((String) body.get("until"));
        String reason = (String) body.get("reason");
        userService.setSuspension(userId, until, reason);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/suspension")
    @Operation(summary = "Lift suspension")
    public ResponseEntity<Void> liftSuspension(@PathVariable String userId) {
        userService.liftSuspension(userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/verification")
    @Operation(summary = "Update KYC verification status")
    public ResponseEntity<Void> updateVerification(@PathVariable String userId,
                                                   @RequestBody Map<String, String> body) {
        VerificationStatus status = VerificationStatus.valueOf(body.get("status"));
        String reason = body.get("rejectionReason");
        userService.updateVerificationStatus(userId, status, reason);
        return ResponseEntity.ok().build();
    }
}