package com.moussefer.reservation.kafka;

import com.moussefer.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmedConsumer {

    private final ReservationService service;

    @KafkaListener(topics = "payment.confirmed", groupId = "reservation-service-group")
    public void onPaymentConfirmed(Map<String, Object> event) {
        String reservationId = (String) event.get("reservationId");
        String paymentIntentId = (String) event.get("paymentId"); // ✅ Fixed: field is "paymentId" in PaymentSucceededEvent
        log.info("Payment confirmed event received for reservation: {}", reservationId);
        service.confirmPayment(reservationId, paymentIntentId);
    }
}