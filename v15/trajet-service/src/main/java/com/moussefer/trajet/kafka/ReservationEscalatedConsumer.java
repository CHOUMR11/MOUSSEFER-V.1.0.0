package com.moussefer.trajet.kafka;

import com.moussefer.trajet.service.TrajetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEscalatedConsumer {

    private final TrajetService trajetService;

    @KafkaListener(topics = "reservation.escalated", groupId = "trajet-service-group")
    public void onReservationEscalated(Map<String, Object> event) {
        String trajetId = (String) event.get("trajetId");
        if (trajetId == null) {
            log.warn("reservation.escalated event missing trajetId: {}", event);
            return;
        }
        log.info("Received reservation.escalated for trajetId: {}", trajetId);
        trajetService.activateNextTrajetForRoute(trajetId);
    }
}