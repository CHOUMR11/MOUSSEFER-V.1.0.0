package com.moussefer.user.controller;

import com.moussefer.user.dto.request.UpdateProfileRequest;
import com.moussefer.user.dto.response.DriverInfoResponse;
import com.moussefer.user.dto.response.UserProfileResponse;
import com.moussefer.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile management")
public class UserController {

    private final UserService userService;

    // Self-service
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PostMapping("/me/deactivate")
    public ResponseEntity<Void> deactivate(@RequestHeader("X-User-Id") String userId) {
        userService.deactivateOwnAccount(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/reactivate")
    public ResponseEntity<Void> reactivate(@RequestHeader("X-User-Id") String userId) {
        userService.reactivateOwnAccount(userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/fcm-token")
    public ResponseEntity<Void> updateFcmToken(@RequestHeader("X-User-Id") String userId,
                                               @RequestBody String token) {
        userService.updateFcmToken(userId, token);
        return ResponseEntity.ok().build();
    }

    // Public driver profile
    @GetMapping("/driver/{driverId}")
    public ResponseEntity<UserProfileResponse> getDriverProfile(@PathVariable String driverId) {
        return ResponseEntity.ok(userService.getProfile(driverId));
    }

    // ========== INTERNAL ENDPOINTS (for other microservices) ==========
    @GetMapping("/internal/{userId}/active")
    public ResponseEntity<Boolean> isActive(@PathVariable String userId) {
        return ResponseEntity.ok(userService.isUserActive(userId));
    }

    @GetMapping("/internal/{userId}")
    public ResponseEntity<DriverInfoResponse> getUserInternal(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserInfoInternal(userId));
    }

    @GetMapping("/internal/drivers")
    public ResponseEntity<java.util.List<DriverInfoResponse>> getAllDrivers() {
        return ResponseEntity.ok(userService.getAllActiveDrivers());
    }

    @GetMapping("/internal/driver/{driverId}/info")
    public ResponseEntity<DriverInfoResponse> getDriverInfoForPayment(@PathVariable String driverId) {
        return ResponseEntity.ok(userService.getDriverInfoForPayment(driverId));
    }
}