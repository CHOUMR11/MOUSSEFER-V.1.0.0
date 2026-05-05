package com.moussefer.loyalty.kafka;

import com.moussefer.loyalty.service.LoyaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * US-111: payment.confirmed → 1 point per dinar (Math.round(amount)).
 * US-92:  voyage.payment.confirmed instead of voyage.reservation.confirmed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoyaltyEventConsumer {

    private static final int VOYAGE_POINTS = 15;

    private final LoyaltyService loyaltyService;

    /** US-111 FIX: Points = Math.round(amount paid in dinars). */
    @KafkaListener(topics = "payment.confirmed", groupId = "loyalty-group")
    public void onPaymentConfirmed(Map<String, Object> event) {
        try {
            String passengerId = getStr(event, "passengerId");
            String reservationId = getStr(event, "reservationId");
            if (passengerId == null || reservationId == null) {
                log.warn("Loyalty: payment.confirmed missing passengerId or reservationId, skipping");
                return;
            }
            int points = extractPointsFromAmount(event);
            loyaltyService.earnPoints(passengerId, points, "TRIP_COMPLETED", reservationId);
            log.info("Loyalty [US-111]: awarded {} pts (1pt/dinar) to user {} for reservation {}", points, passengerId, reservationId);
        } catch (Exception e) {
            log.error("Loyalty: failed to process payment.confirmed: {}", e.getMessage(), e);
        }
    }

    /** US-92 FIX: topic = voyage.payment.confirmed (NOT voyage.reservation.confirmed). */
    @KafkaListener(topics = "voyage.payment.confirmed", groupId = "loyalty-group")
    public void onVoyagePaymentConfirmed(Map<String, Object> event) {
        try {
            String passengerId = getStr(event, "passengerId");
            String reservationId = getStr(event, "reservationId");
            if (passengerId == null || reservationId == null) {
                log.warn("Loyalty: voyage.payment.confirmed missing fields, skipping");
                return;
            }
            loyaltyService.earnPoints(passengerId, VOYAGE_POINTS, "VOYAGE_COMPLETED", reservationId);
            log.info("Loyalty [US-92]: awarded {} voyage points to user {} for reservation {}", VOYAGE_POINTS, passengerId, reservationId);
        } catch (Exception e) {
            log.error("Loyalty: failed to process voyage.payment.confirmed: {}", e.getMessage(), e);
        }
    }

    private int extractPointsFromAmount(Map<String, Object> event) {
        Object amountObj = event.get("amount");
        if (amountObj == null) {
            log.warn("Loyalty: no 'amount' in payment.confirmed event — defaulting to 1 point");
            return 1;
        }
        try {
            double amount = Double.parseDouble(amountObj.toString());
            return Math.max((int) Math.round(amount), 1);
        } catch (NumberFormatException e) {
            log.warn("Loyalty: unparseable amount '{}', defaulting to 1 point", amountObj);
            return 1;
        }
    }

    private String getStr(Map<String, Object> event, String key) {
        Object val = event.get(key);
        return val != null ? val.toString() : null;
    }
}
