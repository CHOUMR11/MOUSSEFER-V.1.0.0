package com.moussefer.demande.kafka;

import com.moussefer.demande.dto.DemandeThresholdReachedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemandeEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "demande.threshold.reached";
    private static final String TOPIC_CONVERTED = "demande.converted";

    public void publishThresholdReached(DemandeThresholdReachedEvent event) {
        kafkaTemplate.send(TOPIC, event.getDemandeId(), event);
        log.info("Published demande.threshold.reached: demandeId={}, totalReserved={}, threshold={}",
                event.getDemandeId(), event.getTotalSeatsReserved(), event.getThresholdUsed());
    }

    public void publishConverted(String demandeId, String driverId, java.util.List<String> passengerIds,
                                 String departureCity, String arrivalCity) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("demandeId", demandeId);
        payload.put("driverId", driverId);
        payload.put("passengerIds", passengerIds);
        payload.put("departureCity", departureCity);
        payload.put("arrivalCity", arrivalCity);
        payload.put("convertedAt", java.time.LocalDateTime.now().toString());
        kafkaTemplate.send(TOPIC_CONVERTED, demandeId, payload);
        log.info("Published demande.converted: demandeId={}, driver={}, passengers={}", demandeId, driverId, passengerIds.size());
    }

    public void publishMerged(String survivorId, java.util.List<String> mergedIds,
                               java.util.List<String> passengerIds,
                               String departureCity, String arrivalCity) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("survivorDemandeId", survivorId);
        payload.put("mergedDemandeIds", mergedIds);
        payload.put("passengerIds", passengerIds);
        payload.put("departureCity", departureCity);
        payload.put("arrivalCity", arrivalCity);
        payload.put("mergedAt", java.time.LocalDateTime.now().toString());
        kafkaTemplate.send("demande.merged", survivorId, payload);
        log.info("Published demande.merged: survivorId={}, merged={}, passengers={}", survivorId, mergedIds.size(), passengerIds.size());
    }

}