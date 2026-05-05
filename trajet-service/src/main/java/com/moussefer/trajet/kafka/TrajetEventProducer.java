package com.moussefer.trajet.kafka;

import com.moussefer.trajet.entity.Trajet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrajetEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendTrajetPublished(Trajet t) {
        kafkaTemplate.send("trajet.published", t.getId(), buildEvent(t, "PUBLISHED"));
        log.info("Event trajet.published sent: {}", t.getId());
    }

    public void sendTrajetDeparted(Trajet t) {
        kafkaTemplate.send("trajet.departed", t.getId(), buildEvent(t, "DEPARTED"));
        log.info("Event trajet.departed sent: {}", t.getId());
    }

    public void sendTrajetActivated(Trajet t) {
        kafkaTemplate.send("trajet.activated", t.getId(), buildEvent(t, "ACTIVATED"));
        log.info("Event trajet.activated sent: {}", t.getId());
    }

    // ✅ Ajout : événement d'annulation
    public void sendTrajetCancelled(Trajet t) {
        kafkaTemplate.send("trajet.cancelled", t.getId(), buildEvent(t, "CANCELLED"));
        log.info("Event trajet.cancelled sent: {}", t.getId());
    }

    private Map<String, Object> buildEvent(Trajet t, String type) {
        return Map.of(
                "type", type,
                "trajetId", t.getId(),
                "driverId", t.getDriverId(),
                "departureCity", t.getDepartureCity(),
                "arrivalCity", t.getArrivalCity(),
                "departureDate", t.getDepartureDate().toString(),
                "availableSeats", t.getAvailableSeats()
        );
    }
}