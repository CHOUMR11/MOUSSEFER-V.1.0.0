package com.moussefer.reservation.kafka;

import com.moussefer.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventProducer {

    private final KafkaTemplate<String, Object> kafka;

    public void sendDriverReminder(Reservation r) {
        kafka.send("reservation.driver.reminder", r.getId(),
                Map.of("reservationId", r.getId(), "driverId", r.getDriverId(),
                        "trajetId", r.getTrajetId(), "passengerId", r.getPassengerId()));
    }

    public void sendAdminAlert(Reservation r) {
        kafka.send("reservation.admin.alert", r.getId(),
                Map.of("reservationId", r.getId(), "driverId", r.getDriverId(),
                        "trajetId", r.getTrajetId(), "passengerId", r.getPassengerId()));
    }

    public void sendReservationEscalated(Reservation r) {
        kafka.send("reservation.escalated", r.getId(),
                Map.of("reservationId", r.getId(), "trajetId", r.getTrajetId(),
                        "driverId", r.getDriverId(), "passengerId", r.getPassengerId()));
    }
}