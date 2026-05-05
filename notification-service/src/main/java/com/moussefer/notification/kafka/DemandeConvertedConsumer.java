package com.moussefer.notification.kafka;

import com.moussefer.notification.entity.NotificationType;
import com.moussefer.notification.repository.NotificationRepository;
import com.moussefer.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemandeConvertedConsumer {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    private static final String NOTIF_TYPE_TAG = "DEMANDE_CONVERTED";

    @KafkaListener(topics = "demande.converted", groupId = "notification-service-group")
    public void onDemandeConverted(Map<String, Object> event) {
        String demandeId = getString(event, "demandeId");
        String departureCity = getString(event, "departureCity");
        String arrivalCity = getString(event, "arrivalCity");

        if (demandeId == null) {
            log.warn("KAFKA-01: demande.converted event missing demandeId, skipping");
            return;
        }

        boolean alreadyNotified = notificationRepository
                .existsByReferenceIdAndReferenceType(demandeId, NOTIF_TYPE_TAG);
        if (alreadyNotified) {
            log.info("KAFKA-01: demande.converted already processed for demandeId={}, skipping", demandeId);
            return;
        }

        Object passengerIdsObj = event.get("passengerIds");
        if (!(passengerIdsObj instanceof List<?> rawList)) {
            log.warn("KAFKA-01: demande.converted missing or invalid passengerIds for demandeId={}", demandeId);
            return;
        }

        List<String> passengerIds = rawList.stream()
                .filter(o -> o instanceof String)
                .map(Object::toString)
                .toList();

        if (passengerIds.isEmpty()) {
            log.info("KAFKA-01: demande.converted no passengers to notify for demandeId={}", demandeId);
            return;
        }

        String title = "🚗 Votre demande collective a été convertie en trajet !";
        String body = String.format(
                "Votre demande collective %s → %s a été convertie en trajet disponible. " +
                        "Réservez maintenant avant que les places ne soient épuisées.",
                departureCity != null ? departureCity : "?",
                arrivalCity != null ? arrivalCity : "?"
        );

        for (String passengerId : passengerIds) {
            try {
                notificationService.send(
                        passengerId,
                        title,
                        body,
                        NotificationType.IN_APP,
                        demandeId,
                        NOTIF_TYPE_TAG,
                        null
                );
                log.info("KAFKA-01: notified passenger {} for demandeId={}", passengerId, demandeId);
            } catch (Exception e) {
                log.error("KAFKA-01: failed to notify passenger {} for demandeId={}: {}", passengerId, demandeId, e.getMessage());
            }
        }

        log.info("KAFKA-01: demande.converted processing complete — {} passengers notified for demandeId={}",
                passengerIds.size(), demandeId);
    }

    @KafkaListener(topics = "demande.merged", groupId = "notification-service-group")
    public void onDemandeMerged(Map<String, Object> event) {
        String survivorDemandeId = getString(event, "survivorDemandeId");
        String departureCity     = getString(event, "departureCity");
        String arrivalCity       = getString(event, "arrivalCity");
        Object passengersObj     = event.get("passengerIds");

        if (survivorDemandeId == null || passengersObj == null) {
            log.warn("FIX #16: demande.merged missing required fields, skipping");
            return;
        }

        List<String> passengerIds = passengersObj instanceof List<?> list
                ? list.stream().filter(o -> o instanceof String).map(Object::toString).toList()
                : List.of();

        String route = (departureCity != null ? departureCity : "?") + " → " + (arrivalCity != null ? arrivalCity : "?");
        String title = "Vos demandes ont été fusionnées";
        String body  = String.format("Vos demandes pour la route %s ont été regroupées pour atteindre plus vite le seuil d'activation.", route);

        for (String passengerId : passengerIds) {
            try {
                notificationService.send(
                        passengerId, title, body,
                        NotificationType.IN_APP, survivorDemandeId,
                        "DEMANDE_MERGED", null
                );
                log.info("FIX #16: passenger {} notified of demande.merged -> survivorId={}", passengerId, survivorDemandeId);
            } catch (Exception e) {
                log.error("FIX #16: failed to notify passenger {} for merged demande: {}", passengerId, e.getMessage());
            }
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }
}