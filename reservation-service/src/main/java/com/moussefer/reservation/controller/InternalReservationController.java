package com.moussefer.reservation.controller;

import com.moussefer.reservation.repository.ReservationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservations/internal")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Reservations – Internal", description = "Internal endpoints for service-to-service communication")
public class InternalReservationController {

    private final ReservationRepository reservationRepository;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @GetMapping("/check-contact-access")
    @Operation(summary = "Check if passenger has a confirmed/paid reservation with driver (SEC-01)")
    public ResponseEntity<Map<String, Boolean>> checkContactAccess(
            @RequestParam String passengerId,
            @RequestParam String driverId,
            @RequestHeader("X-Internal-Secret") String secret) {

        if (!internalApiKey.equals(secret)) {
            log.warn("SEC-01: Unauthorized internal call from unknown source");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        boolean hasAccess = reservationRepository.hasConfirmedPaymentForDriver(passengerId, driverId);
        log.info("SEC-01: contact-access check passengerId={} driverId={} → {}", passengerId, driverId, hasAccess);
        return ResponseEntity.ok(Map.of("hasAccess", hasAccess));
    }
}