package com.moussefer.analytics.kafka;

import com.moussefer.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes Kafka events from the event bus to build analytics data.
 * Each event is recorded as a TripEvent for dashboard reporting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final AnalyticsService analyticsService;

    @KafkaListener(topics = "reservation.created", groupId = "analytics-group")
    public void onReservationCreated(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "BOOKED",
                    getStr(event, "trajetId"),
                    getStr(event, "reservationId"),
                    getStr(event, "passengerId"),
                    getStr(event, "driverId"),
                    getStr(event, "departureCity"),
                    getStr(event, "arrivalCity"),
                    getDouble(event, "totalPrice")
            );
            log.info("Analytics: recorded BOOKED event for reservation {}", event.get("reservationId"));
        } catch (Exception e) {
            log.error("Analytics: failed to record BOOKED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.confirmed", groupId = "analytics-group")
    public void onPaymentConfirmed(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "COMPLETED",
                    getStr(event, "trajetId"),
                    getStr(event, "reservationId"),
                    getStr(event, "passengerId"),
                    getStr(event, "driverId"),
                    null,
                    null,
                    getDouble(event, "amount")
            );
            log.info("Analytics: recorded COMPLETED event for reservation {}", event.get("reservationId"));
        } catch (Exception e) {
            log.error("Analytics: failed to record COMPLETED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "reservation.cancelled", groupId = "analytics-group")
    public void onReservationCancelled(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "CANCELLED",
                    getStr(event, "trajetId"),
                    getStr(event, "reservationId"),
                    getStr(event, "passengerId"),
                    getStr(event, "driverId"),
                    null,
                    null,
                    null
            );
            log.info("Analytics: recorded CANCELLED event for reservation {}", event.get("reservationId"));
        } catch (Exception e) {
            log.error("Analytics: failed to record CANCELLED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "reservation.escalated", groupId = "analytics-group")
    public void onReservationEscalated(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "ESCALATED",
                    getStr(event, "trajetId"),
                    getStr(event, "reservationId"),
                    getStr(event, "passengerId"),
                    null,
                    null,
                    null,
                    null
            );
            log.info("Analytics: recorded ESCALATED event for reservation {}", event.get("reservationId"));
        } catch (Exception e) {
            log.error("Analytics: failed to record ESCALATED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "trajet.published", groupId = "analytics-group")
    public void onTrajetPublished(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "TRAJET_PUBLISHED",
                    getStr(event, "trajetId"),
                    null,
                    null,
                    getStr(event, "driverId"),
                    getStr(event, "departureCity"),
                    getStr(event, "arrivalCity"),
                    null
            );
        } catch (Exception e) {
            log.error("Analytics: failed to record TRAJET_PUBLISHED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "user.registered", groupId = "analytics-group")
    public void onUserRegistered(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "USER_REGISTERED",
                    null,
                    null,
                    getStr(event, "userId"),
                    null,
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("Analytics: failed to record USER_REGISTERED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "trajet.departed", groupId = "analytics-group")
    public void onTrajetDeparted(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "TRAJET_DEPARTED",
                    getStr(event, "trajetId"),
                    null,
                    null,
                    getStr(event, "driverId"),
                    getStr(event, "departureCity"),
                    getStr(event, "arrivalCity"),
                    null
            );
        } catch (Exception e) {
            log.error("Analytics: failed to record TRAJET_DEPARTED event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "payment.failed", groupId = "analytics-group")
    public void onPaymentFailed(Map<String, Object> event) {
        try {
            analyticsService.recordEvent(
                    "PAYMENT_FAILED",
                    null,
                    getStr(event, "reservationId"),
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } catch (Exception e) {
            log.error("Analytics: failed to record PAYMENT_FAILED event: {}", e.getMessage(), e);
        }
    }

    private String getStr(Map<String, Object> event, String key) {
        Object val = event.get(key);
        return val != null ? val.toString() : null;
    }

    private Double getDouble(Map<String, Object> event, String key) {
        Object val = event.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
