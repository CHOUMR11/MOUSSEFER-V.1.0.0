package com.moussefer.reservation.service;

import com.moussefer.reservation.entity.Reservation;
import com.moussefer.reservation.entity.ReservationStatus;
import com.moussefer.reservation.exception.ResourceNotFoundException;
import com.moussefer.reservation.kafka.ReservationEventProducer;
import com.moussefer.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationSchedulerService {

    private final ReservationRepository reservationRepository;
    private final ReservationEventProducer eventProducer;
    private final WebClient trajetServiceWebClient;

    @Value("${internal.api-key:dev-internal-key}")
    private String internalApiKey;

    @Transactional
    public void sendReminderForOne(String reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        if (reservation.isReminderSent() || reservation.getStatus() != ReservationStatus.PENDING_DRIVER) {
            return;
        }

        reservation.setReminderSent(true);
        reservationRepository.save(reservation);
        eventProducer.sendDriverReminder(reservation);
        log.info("Reminder sent to driver: reservationId={} driverId={}", reservation.getId(), reservation.getDriverId());
    }

    @Transactional
    public void notifyAdminForOne(String reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        if (reservation.isAdminNotified() || reservation.getStatus() != ReservationStatus.PENDING_DRIVER) {
            return;
        }

        reservation.setAdminNotified(true);
        reservationRepository.save(reservation);
        eventProducer.sendAdminAlert(reservation);
        log.warn("Admin notified: reservation {} still pending after 8 min, driverId={}", reservation.getId(), reservation.getDriverId());
    }

    @Transactional
    public void escalateOne(String reservationId) {
        Reservation reservation = findOrThrow(reservationId);
        if (reservation.isEscalated() || reservation.getStatus() != ReservationStatus.PENDING_DRIVER) {
            return;
        }

        reservation.setStatus(ReservationStatus.ESCALATED);
        reservation.setEscalated(true);
        reservation.setCancelledAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        // CRITICAL: Release temporarily reserved seats back to trajet
        releaseSeats(reservation);

        eventProducer.sendReservationEscalated(reservation);
        log.warn("Reservation escalated (driver timeout): id={} driverId={}", reservation.getId(), reservation.getDriverId());
    }

    private void releaseSeats(Reservation r) {
        try {
            trajetServiceWebClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/trajets/internal/{id}/release-temp")
                            .queryParam("seats", r.getSeatsReserved())
                            .build(r.getTrajetId()))
                    .header("X-Internal-Secret", internalApiKey)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofSeconds(5));
            log.info("Released {} temp seats on trajet {} after escalation", r.getSeatsReserved(), r.getTrajetId());
        } catch (Exception e) {
            log.error("Failed to release seats for trajet {} after escalation: {}", r.getTrajetId(), e.getMessage());
        }
    }

    private Reservation findOrThrow(String id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
    }
}