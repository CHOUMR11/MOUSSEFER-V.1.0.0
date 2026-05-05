package com.moussefer.payment.kafka;

import com.moussefer.payment.event.PaymentSucceededEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentSucceeded(PaymentSucceededEvent event) {
        kafkaTemplate.send("payment.confirmed", event.getReservationId(), event);
        log.info("Published payment.confirmed: reservationId={}", event.getReservationId());
    }

    public void publishPaymentFailed(String reservationId, String reason) {
        kafkaTemplate.send("payment.failed", reservationId,
                Map.of("reservationId", reservationId, "reason", reason));
        log.warn("Published payment.failed: reservationId={}, reason={}", reservationId, reason);
    }
}