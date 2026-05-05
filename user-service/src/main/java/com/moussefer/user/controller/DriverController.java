package com.moussefer.user.controller;

import com.moussefer.user.dto.response.DriverInfoResponse;
import com.moussefer.user.dto.response.UserProfileResponse;
import com.moussefer.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
@Slf4j
public class DriverController {

    private final UserService userService;
    private final WebClient reservationServiceWebClient;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @GetMapping("/{driverId}/info")
    public ResponseEntity<UserProfileResponse> getDriverInfo(@PathVariable String driverId) {
        return ResponseEntity.ok(userService.getProfile(driverId));
    }

    @GetMapping("/{driverId}/contact")
    public ResponseEntity<DriverInfoResponse> getDriverContact(
            @PathVariable String driverId,
            @RequestHeader(value = "X-User-Id", required = false) String passengerId) {

        if (passengerId == null || passengerId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean hasAccess = checkContactAccess(passengerId, driverId);
        if (!hasAccess) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(userService.getDriverInfoForPayment(driverId));
    }

    private boolean checkContactAccess(String passengerId, String driverId) {
        try {
            Map<?, ?> result = reservationServiceWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/reservations/internal/check-contact-access")
                            .queryParam("passengerId", passengerId)
                            .queryParam("driverId", driverId)
                            .build())
                    .header("X-Internal-Secret", internalApiKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(5));
            return result != null && Boolean.TRUE.equals(result.get("hasAccess"));
        } catch (Exception e) {
            log.error("Contact access check failed: {}", e.getMessage());
            return false;
        }
    }
}