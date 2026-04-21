package com.moussefer.chat.kafka;

import com.moussefer.chat.dto.ChatActivationEvent;
import com.moussefer.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoyageReservationConfirmedConsumer {

    private final ChatService chatService;

    @KafkaListener(topics = "voyage.reservation.confirmed", groupId = "chat-service-group")
    public void onVoyageReservationConfirmed(Map<String, Object> event) {
        Object rawReservation = event.get("voyageReservationId");  // ou "reservationId" selon contrat
        Object rawPassenger = event.get("passengerId");
        Object rawOrganizer = event.get("organizerId");
        Object rawDeparture = event.get("departureTime");

        if (!(rawReservation instanceof String reservationId) ||
                !(rawPassenger instanceof String passengerId) ||
                !(rawOrganizer instanceof String organizerId)) {
            log.warn("voyage.reservation.confirmed event missing required fields: {}", event);
            return;
        }

        LocalDateTime departureTime = LocalDateTime.now().plusHours(6);
        if (rawDeparture instanceof Number timestamp) {
            departureTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp.longValue()), ZoneId.systemDefault());
        }

        ChatActivationEvent activationEvent = new ChatActivationEvent(
                reservationId,
                "ORGANIZED",
                passengerId,
                organizerId,
                departureTime
        );
        chatService.activateSession(activationEvent);
        log.info("Chat session activated for voyage reservation: {}", reservationId);
    }
}