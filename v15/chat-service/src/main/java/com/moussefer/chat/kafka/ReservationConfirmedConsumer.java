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
public class ReservationConfirmedConsumer {

    private final ChatService chatService;

    @KafkaListener(topics = "reservation.confirmed", groupId = "chat-service-group")
    public void onReservationConfirmed(Map<String, Object> event) {
        Object rawReservation = event.get("reservationId");
        Object rawPassenger = event.get("passengerId");
        Object rawDriver = event.get("driverId");
        Object rawDeparture = event.get("departureTime");

        if (!(rawReservation instanceof String reservationId) ||
                !(rawPassenger instanceof String passengerId) ||
                !(rawDriver instanceof String driverId)) {
            log.warn("reservation.confirmed event missing required fields: {}", event);
            return;
        }

        LocalDateTime departureTime = parseDepartureTime(rawDeparture, reservationId);

        ChatActivationEvent activationEvent = new ChatActivationEvent(
                reservationId,
                "RIDE",
                passengerId,
                driverId,
                departureTime
        );
        chatService.activateSession(activationEvent);
        log.info("Chat session activated for reservation: {}", reservationId);
    }

    /**
     * Robust parser handling multiple timestamp formats from Kafka events:
     * - Epoch seconds (Long/Integer)
     * - Epoch millis (Long > 10^12)
     * - ISO-8601 with/without offset/zone
     * - ISO-8601 with space separator
     * Returns a sensible default (now + 6h) if parsing completely fails.
     */
    private LocalDateTime parseDepartureTime(Object raw, String reservationId) {
        if (raw instanceof Number n) {
            long epoch = n.longValue();
            if (epoch > 10_000_000_000L) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
            }
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        }
        if (raw instanceof String s && !s.isBlank()) {
            String normalized = s.trim().replace(' ', 'T');
            try { return LocalDateTime.parse(normalized); } catch (Exception ignore) {}
            try { return java.time.OffsetDateTime.parse(normalized).toLocalDateTime(); } catch (Exception ignore) {}
            try { return java.time.ZonedDateTime.parse(normalized).toLocalDateTime(); } catch (Exception ignore) {}
            try { return Instant.parse(normalized).atZone(ZoneId.systemDefault()).toLocalDateTime(); } catch (Exception ignore) {}
            log.warn("Could not parse departureTime '{}' for reservation {}, using fallback now+6h", s, reservationId);
        }
        return LocalDateTime.now().plusHours(6);
    }
}