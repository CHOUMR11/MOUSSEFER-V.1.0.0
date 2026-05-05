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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final PaymentRepository paymentRepository;
    private final StripeWebhookEventRepository webhookEventRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentAsyncHandler paymentAsyncHandler;
    private final WebClient userServiceWebClient;
    private final PromoCodeService promoCodeService;
    private final ObjectMapper objectMapper;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Transactional
    public void handlePaymentIntentSucceeded(String eventId, PaymentIntent paymentIntent) {
        // 1. Idempotence : vérifier si l'événement a déjà été traité
        if (webhookEventRepository.findByEventId(eventId).isPresent()) {
            log.info("Webhook event {} already processed, skipping", eventId);
            return;
        }

        // 2. Sauvegarder l'événement immédiatement pour garantir l'idempotence
        StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                .eventId(eventId)
                .eventType("payment_intent.succeeded")
                .build();
        webhookEventRepository.save(webhookEvent);

        // 3. Récupérer et valider le paiement
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntent.getId())
                .orElseThrow(() -> new RuntimeException("Payment not found for stripeId: " + paymentIntent.getId()));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed (status={}), ignoring webhook", payment.getId(), payment.getStatus());
            return;
        }

        // 4. Mettre à jour le statut du paiement
        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        // 5. Événement outbox pour reservation-service
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                payment.getId(),
                payment.getReservationId(),
                payment.getPassengerId(),
                payment.getDriverId(),
                payment.getAmount(),
                payment.getCurrency(),
                null   // invoiceUrl sera générée asynchrone
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

        // 6. Incrémenter le compteur du code promo
        if (payment.getPromoCode() != null && !payment.getPromoCode().isBlank()) {
            promoCodeService.markPromoCodeUsed(payment.getPromoCode());
            log.info("Promo code {} marked as used after successful payment", payment.getPromoCode());
        }

        // 7. Attribuer les points de fidélité (1 point par euro, non bloquant)
        try {
            int pointsToAdd = payment.getAmount()
                    .setScale(0, RoundingMode.HALF_UP).intValue();
            if (pointsToAdd > 0) {
                userServiceWebClient.post()
                        .uri("/api/v1/users/internal/{userId}/loyalty-points/add", payment.getPassengerId())
                        .header("X-Internal-Secret", internalApiKey)
                        .bodyValue(Map.of("points", pointsToAdd,
                                "reason", "Payment for reservation " + payment.getReservationId()))
                        .retrieve()
                        .bodyToMono(Void.class)
                        .timeout(Duration.ofSeconds(3))
                        .doOnSuccess(ignored -> log.info("Loyalty points awarded: {} pts to passenger {}",
                                pointsToAdd, payment.getPassengerId()))
                        .doOnError(e -> log.warn("Failed to award loyalty points for passenger {}: {}",
                                payment.getPassengerId(), e.getMessage()))
                        .subscribe(); // fire-and-forget
            }
        } catch (Exception e) {
            log.warn("Loyalty points integration error (non-blocking): {}", e.getMessage());
        }

        // 8. Générer la facture PDF de manière asynchrone
        paymentAsyncHandler.generateAndStoreInvoice(payment.getId());

        log.info("Payment succeeded for reservation: {}", payment.getReservationId());
    }

    @Transactional
    public void handlePaymentIntentFailed(String eventId, PaymentIntent paymentIntent) {
        if (webhookEventRepository.findByEventId(eventId).isPresent()) {
            log.info("Webhook event {} already processed, skipping", eventId);
            return;
        }

        // Sauvegarder l'événement immédiatement
        StripeWebhookEvent webhookEvent = StripeWebhookEvent.builder()
                .eventId(eventId)
                .eventType("payment_intent.payment_failed")
                .build();
        webhookEventRepository.save(webhookEvent);

        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntent.getId()).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} not in PENDING state, ignoring webhook", payment != null ? payment.getId() : "unknown");
            return;
        }

        payment.setStatus(PaymentStatus.FAILED);
        paymentRepository.save(payment);

        // Événement outbox pour notifier la réservation (optionnel)
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