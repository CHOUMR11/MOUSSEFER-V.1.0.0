package com.moussefer.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moussefer.payment.entity.OutboxEvent;
import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.entity.PaymentStatus;
import com.moussefer.payment.entity.StripeWebhookEvent;
import com.moussefer.payment.event.PaymentSucceededEvent;
import com.moussefer.payment.repository.OutboxEventRepository;
import com.moussefer.payment.repository.PaymentRepository;
import com.moussefer.payment.repository.StripeWebhookEventRepository;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final PaymentRepository paymentRepository;
    private final StripeWebhookEventRepository webhookEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentAsyncHandler paymentAsyncHandler;
    private final WebClient userServiceWebClient;
    private final PromoCodeService promoCodeService;       // ✅ Nouvelle dépendance
    private final ObjectMapper objectMapper;

    @Transactional
    public void handlePaymentIntentSucceeded(String eventId, PaymentIntent paymentIntent) {
        if (webhookEventRepository.findByEventId(eventId).isPresent()) {
            log.info("Webhook event {} already processed, skipping", eventId);
            return;
        }

        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntent.getId())
                .orElseThrow(() -> new RuntimeException("Payment not found for stripeId: " + paymentIntent.getId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed, ignoring webhook", payment.getId());
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                .eventId(eventId)
                .eventType("payment_intent.succeeded")
                .build();
        webhookEventRepository.save(webhookEvent);

        PaymentSucceededEvent event = new PaymentSucceededEvent(
                payment.getId(),
                payment.getReservationId(),
                payment.getPassengerId(),
                payment.getDriverId(),
                payment.getAmount(),
                payment.getCurrency(),
                null   // invoiceUrl generated asynchronously after
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(payment.getReservationId())
                    .eventType("payment.confirmed")
                    .payload(payload)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to create outbox event for payment {}", payment.getId(), e);
        }

        // ✅ Correction risque code promo : marqué utilisé APRÈS confirmation du paiement
        if (payment.getPromoCode() != null && !payment.getPromoCode().isBlank()) {
            promoCodeService.markPromoCodeUsed(payment.getPromoCode());
            log.info("Promo code {} marked as used after successful payment", payment.getPromoCode());
        }
        // Award loyalty points: 1 point per euro paid (configurable)
        try {
            int pointsToAdd = payment.getAmount().intValue();
            if (pointsToAdd > 0) {
                userServiceWebClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/users/internal/{userId}/loyalty-points/add")
                                .build(payment.getPassengerId()))
                        .bodyValue(java.util.Map.of("points", pointsToAdd,
                                "reason", "Payment for reservation " + payment.getReservationId()))
                        .retrieve()
                        .bodyToMono(Void.class)
                        .timeout(java.time.Duration.ofSeconds(3))
                        .subscribe(
                                v -> log.info("Loyalty points awarded: {} pts to passenger {}",
                                        pointsToAdd, payment.getPassengerId()),
                                e -> log.warn("Failed to award loyalty points for passenger {}: {}",
                                        payment.getPassengerId(), e.getMessage())
                        );
            }
        } catch (Exception e) {
            log.warn("Loyalty points integration error (non-blocking): {}", e.getMessage());
        }


        paymentAsyncHandler.generateAndStoreInvoice(payment.getId());
        log.info("Payment succeeded for reservation: {}", payment.getReservationId());
    }

    @Transactional
    public void handlePaymentIntentFailed(String eventId, PaymentIntent paymentIntent) {
        if (webhookEventRepository.findByEventId(eventId).isPresent()) {
            log.info("Webhook event {} already processed, skipping", eventId);
            return;
        }

        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntent.getId()).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                .eventId(eventId)
                .eventType("payment_intent.payment_failed")
                .build();
        webhookEventRepository.save(webhookEvent);

        try {
            String payload = String.format("{\"reservationId\":\"%s\",\"reason\":\"%s\"}",
                    payment.getReservationId(),
                    paymentIntent.getLastPaymentError() != null ? paymentIntent.getLastPaymentError().getMessage() : "Unknown error");
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(payment.getReservationId())
                    .eventType("payment.failed")
                    .payload(payload)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to create outbox event for failed payment {}", payment.getId(), e);
        }

        log.warn("Payment failed for reservation: {}", payment.getReservationId());
    }
}