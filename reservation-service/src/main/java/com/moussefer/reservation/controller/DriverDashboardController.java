package com.moussefer.reservation.controller;

import com.moussefer.reservation.dto.response.ReservationResponse;
import com.moussefer.reservation.entity.Reservation;
import com.moussefer.reservation.entity.ReservationStatus;
import com.moussefer.reservation.repository.ReservationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/reservations/driver")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Driver Dashboard", description = "Data feeds for driver sidebar screens")
public class DriverDashboardController {

    private final ReservationRepository reservationRepository;

    @Value("${driver.commission.rate:0.90}")
    private BigDecimal commissionRate;

    private void requireDriver(String role) {
        if (!"DRIVER".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only DRIVER role can access this endpoint");
        }
    }

    @GetMapping("/dashboard")
    @Operation(summary = "KPI cards for the driver dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestHeader("X-User-Id") String driverId,
            @RequestHeader("X-User-Role") String role) {
        requireDriver(role);

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        List<Reservation> reservations = reservationRepository.findByDriverId(driverId);

        List<Reservation> thisMonthConfirmed = reservations.stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(monthStart))
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .toList();

        int trajetsThisMonth = (int) thisMonthConfirmed.stream()
                .map(Reservation::getTrajetId)
                .distinct()
                .count();

        BigDecimal grossRevenue = thisMonthConfirmed.stream()
                .filter(r -> r.getTotalPrice() != null)
                .map(Reservation::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal driverShare = grossRevenue.multiply(commissionRate)
                .setScale(2, RoundingMode.HALF_UP);

        long pendingDemandes = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING_DRIVER)
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("driverId", driverId);
        result.put("trajetsThisMonth", trajetsThisMonth);
        result.put("grossRevenueThisMonth", grossRevenue);
        result.put("netRevenueThisMonth", driverShare);
        result.put("pendingDemandes", pendingDemandes);
        result.put("ratingEndpoint", "/api/v1/avis/driver/" + driverId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/active-trip")
    @Operation(summary = "Active trajet and its confirmed passengers")
    public ResponseEntity<Map<String, Object>> activeTrip(
            @RequestHeader("X-User-Id") String driverId,
            @RequestHeader("X-User-Role") String role) {
        requireDriver(role);

        List<Reservation> driverReservations = reservationRepository.findByDriverId(driverId);
        Optional<String> activeTrajetId = driverReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .filter(r -> r.getDepartureDate() != null
                        && r.getDepartureDate().isAfter(LocalDateTime.now().minusHours(1)))
                .sorted(Comparator.comparing(Reservation::getDepartureDate))
                .map(Reservation::getTrajetId)
                .findFirst();

        if (activeTrajetId.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "hasActiveTrip", false,
                    "message", "No active trajet at the moment"));
        }

        String trajetId = activeTrajetId.get();
        List<Reservation> trajetReservations = driverReservations.stream()
                .filter(r -> trajetId.equals(r.getTrajetId()))
                .toList();

        List<Map<String, Object>> confirmedPassengers = trajetReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .map(this::buildPassengerSummary)
                .toList();

        int totalSeatsConfirmed = trajetReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .mapToInt(Reservation::getSeatsReserved)
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hasActiveTrip", true);
        result.put("trajetId", trajetId);
        result.put("totalSeatsConfirmed", totalSeatsConfirmed);
        result.put("confirmedPassengers", confirmedPassengers);
        result.put("reservationRequestsEndpoint", "/api/v1/reservations/driver/pending");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/active-passengers")
    @Operation(summary = "List of confirmed passengers on upcoming trips")
    public ResponseEntity<Map<String, Object>> activePassengers(
            @RequestHeader("X-User-Id") String driverId,
            @RequestHeader("X-User-Role") String role) {
        requireDriver(role);

        List<Reservation> reservations = reservationRepository.findByDriverId(driverId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        List<Reservation> confirmedUpcoming = reservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .filter(r -> r.getDepartureDate() != null && r.getDepartureDate().isAfter(now))
                .toList();

        List<Map<String, Object>> passengers = confirmedUpcoming.stream()
                .map(this::buildPassengerSummary)
                .toList();

        long totalThisMonth = reservations.stream()
                .filter(r -> r.getCreatedAt() != null && !r.getCreatedAt().isBefore(monthStart))
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("driverId", driverId);
        result.put("activeCount", passengers.size());
        result.put("totalThisMonth", totalThisMonth);
        result.put("passengers", passengers);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/history")
    @Operation(summary = "Past reservations (history view)")
    public ResponseEntity<List<ReservationResponse>> history(
            @RequestHeader("X-User-Id") String driverId,
            @RequestHeader("X-User-Role") String role) {
        requireDriver(role);

        List<Reservation> reservations = reservationRepository.findByDriverId(driverId);
        LocalDateTime now = LocalDateTime.now();
        List<ReservationResponse> history = reservations.stream()
                .filter(r -> (r.getStatus() == ReservationStatus.CONFIRMED
                        && r.getDepartureDate() != null
                        && r.getDepartureDate().isBefore(now))
                        || r.getStatus() == ReservationStatus.CANCELLED
                        || r.getStatus() == ReservationStatus.REFUSED)
                .sorted(Comparator.comparing(Reservation::getCreatedAt).reversed())
                .limit(100)
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(history);
    }

    private Map<String, Object> buildPassengerSummary(Reservation r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reservationId", r.getId());
        m.put("passengerId", r.getPassengerId());
        m.put("seatsReserved", r.getSeatsReserved());
        m.put("totalPrice", r.getTotalPrice());
        m.put("paymentMethod", r.getPaymentMethod() != null ? r.getPaymentMethod().name() : "ONLINE");
        m.put("status", r.getStatus().name());
        return m;
    }

    private ReservationResponse toResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .trajetId(r.getTrajetId())
                .passengerId(r.getPassengerId())
                .driverId(r.getDriverId())
                .seatsReserved(r.getSeatsReserved())
                .totalPrice(r.getTotalPrice())
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .confirmedAt(r.getConfirmedAt())
                .paidAt(r.getPaidAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}