package com.moussefer.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.entity.PaymentStatus;
import com.moussefer.payment.repository.PaymentRepository;
import com.moussefer.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * V22 — Auto-refund on escalation and priority-override cancellation.
 *
 * The reservation scenarios document (Edge Case C) specifies that when a
 * reservation escalates due to driver timeout, the passenger must be
 * refunded automatically if they had already paid. The previous backend
 * raised the `reservation.escalated` event but no consumer performed the
 * refund — the admin had to do it manually.
 *
 * This consumer listens to three events:
 *
 *   reservation.escalated  — driver didn't respond within 15 min.
 *                            If the payment went through (rare race
 *                            condition but possible), refund it.
 *
 *   reservation.refused    — driver explicitly refused. Same logic.
 *
 *   reservation.cancelled  — general cancellation (passenger annulation).
 *                            Refund uniformly when the payment was
 *                            already SUCCEEDED.
 *
 * Idempotent: if the payment is already REFUNDED, we log and skip.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationEventConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "reservation.escalated", groupId = "payment-refund-group")
    public void onEscalated(String message) {
        handleRefundTrigger(message, "ESCALATION_TIMEOUT",
                "Driver did not respond within 15 minutes");
    }

    @KafkaListener(topics = "reservation.refused", groupId = "payment-refund-group")
    public void onRefused(String message) {
        handleRefundTrigger(message, "DRIVER_REFUSED",
                "Driver refused the reservation");
    }

    @KafkaListener(topics = "reservation.cancelled", groupId = "payment-refund-group")
    public void onCancelled(String message) {
        handleRefundTrigger(message, "CANCELLATION", "Reservation cancelled");
    }

    private void handleRefundTrigger(String message, String triggerType, String reason) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String reservationId = event.has("reservationId")
                    ? event.get("reservationId").asText() : null;
            if (reservationId == null || reservationId.isBlank()) {
                log.warn("Refund trigger {} missing reservationId, skipping", triggerType);
                return;
            }

            Optional<Payment> paymentOpt = paymentRepository.findByReservationId(reservationId);
            if (paymentOpt.isEmpty()) {
                // No payment row — passenger never paid, nothing to refund.
                log.debug("No payment for reservation {} on {} — skipping refund", reservationId, triggerType);
                return;
            }
            Payment payment = paymentOpt.get();

            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                log.info("Payment {} for reservation {} already refunded — skipping",
                        payment.getId(), reservationId);
                return;
            }
            if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
                // Payment never went through (PENDING, FAILED, etc.) — nothing to refund.
                log.debug("Payment {} not in SUCCEEDED state (current: {}) — skipping refund",
                        payment.getId(), payment.getStatus());
                return;
            }

            // Full refund — use the auto-refund actor identifier
            try {
                BigDecimal amount = payment.getAmount();
                paymentService.refundPayment(payment.getId(), "system:" + triggerType.toLowerCase(), amount);
                log.info("Auto-refund executed: reservationId={}, paymentId={}, trigger={}, amount={} DT, reason='{}'",
                        reservationId, payment.getId(), triggerType, amount, reason);
            } catch (Exception e) {
                log.error("Auto-refund FAILED for paymentId={}, reservationId={}, trigger={}: {}",
                        payment.getId(), reservationId, triggerType, e.getMessage());
                // Do not re-throw — if refund fails, the admin is already notified
                // by the escalation event itself. Retry logic could be added via
                // a dead-letter topic in a future version.
            }
        } catch (Exception e) {
            log.error("Unable to process {} event: {}", triggerType, e.getMessage(), e);
        }
    }
}
