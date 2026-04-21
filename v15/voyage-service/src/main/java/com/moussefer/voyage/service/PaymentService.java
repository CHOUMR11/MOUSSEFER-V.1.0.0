package com.moussefer.voyage.service;

import com.moussefer.voyage.dto.response.PaymentInitiationResponse;
import com.moussefer.voyage.entity.ReservationVoyage;
import com.moussefer.voyage.entity.ReservationVoyageStatus;
import com.moussefer.voyage.entity.Voyage;
import com.moussefer.voyage.entity.VoyageStatus;
import com.moussefer.voyage.kafka.VoyageEventProducer;
import com.moussefer.voyage.repository.ReservationVoyageRepository;
import com.moussefer.voyage.repository.VoyageRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final ReservationVoyageRepository reservationRepository;
    private final VoyageRepository voyageRepository;
    private final VoyageEventProducer eventProducer;

    /**
     * Initialise un PaymentIntent Stripe pour une réservation de voyage.
     *
     * @param reservationId ID de la réservation
     * @return Réponse contenant le clientSecret pour le frontend
     */
    @Transactional
    public PaymentInitiationResponse initiatePayment(String reservationId) {
        ReservationVoyage reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        if (reservation.getStatus() != ReservationVoyageStatus.PENDING_ORGANIZER) {
            throw new RuntimeException("Reservation not ready for payment. Current status: " + reservation.getStatus());
        }

        Voyage voyage = voyageRepository.findById(reservation.getVoyageId())
                .orElseThrow(() -> new RuntimeException("Voyage not found: " + reservation.getVoyageId()));

        long amountInCents = (long) (reservation.getTotalPrice() * 100);
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("eur")
                .setDescription("Voyage organisé Moussefer: " + voyage.getDepartureCity() + " → " + voyage.getArrivalCity())
                .putMetadata("reservationId", reservationId)
                .putMetadata("voyageId", voyage.getId())
                .putMetadata("passengerId", reservation.getPassengerId())
                .build();

        try {
            PaymentIntent intent = PaymentIntent.create(params);
            reservation.setPaymentIntentId(intent.getId());
            reservationRepository.save(reservation);

            log.info("PaymentIntent created: intentId={}, reservationId={}, amount={}€",
                    intent.getId(), reservationId, reservation.getTotalPrice());

            return PaymentInitiationResponse.builder()
                    .paymentId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .amount(amountInCents)
                    .currency("eur")
                    .build();
        } catch (StripeException e) {
            log.error("Stripe payment initiation failed for reservation {}", reservationId, e);
            throw new RuntimeException("Payment initiation failed: " + e.getMessage());
        }
    }

    /**
     * Confirme le paiement après réception du webhook Stripe.
     * Met à jour le statut de la réservation et les places disponibles.
     *
     * @param paymentIntentId ID du PaymentIntent Stripe
     * @param reservationId   ID de la réservation
     */
    @Transactional
    public void confirmPayment(String paymentIntentId, String reservationId) {
        // Vérification préalable du statut Stripe (idempotence et sécurité)
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            if (!"succeeded".equals(intent.getStatus())) {
                log.warn("PaymentIntent {} status is '{}', expected 'succeeded'. Cannot confirm reservation {}",
                        paymentIntentId, intent.getStatus(), reservationId);
                throw new RuntimeException("Payment not succeeded. Current status: " + intent.getStatus());
            }
        } catch (StripeException e) {
            log.error("Failed to retrieve PaymentIntent {} from Stripe", paymentIntentId, e);
            throw new RuntimeException("Unable to verify payment status with Stripe");
        }

        ReservationVoyage reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Reservation not found: " + reservationId));

        // Éviter les doubles confirmations
        if (reservation.getStatus() == ReservationVoyageStatus.CONFIRMED && reservation.getPaidAt() != null) {
            log.info("Reservation {} already confirmed, skipping", reservationId);
            return;
        }

        reservation.setStatus(ReservationVoyageStatus.CONFIRMED);
        reservation.setPaidAt(java.time.LocalDateTime.now());
        reservationRepository.save(reservation);

        // Mise à jour des places disponibles du voyage
        Voyage voyage = voyageRepository.findById(reservation.getVoyageId())
                .orElseThrow(() -> new RuntimeException("Voyage not found: " + reservation.getVoyageId()));

        int newAvailableSeats = voyage.getAvailableSeats() - reservation.getSeatsReserved();
        if (newAvailableSeats < 0) {
            throw new RuntimeException("Inconsistent seat count for voyage " + voyage.getId());
        }
        voyage.setAvailableSeats(newAvailableSeats);
        if (newAvailableSeats == 0) {
            voyage.setStatus(VoyageStatus.FULL);
        }
        voyageRepository.save(voyage);

        // Émettre l'événement Kafka pour notifier les autres services
        eventProducer.sendPaymentConfirmed(reservation,
                voyage.getDepartureCity(), voyage.getArrivalCity(),
                voyage.getDepartureDate().toString());

        log.info("Payment confirmed for reservation {}: seats updated, voyage {} now has {} available seats",
                reservationId, voyage.getId(), newAvailableSeats);
    }

    /**
     * Rembourse une réservation payée (appelé lors de l'annulation du voyage).
     *
     * @param reservation La réservation à rembourser
     */
    public void refundReservation(ReservationVoyage reservation) {
        if (reservation.getPaymentIntentId() == null || reservation.getPaidAt() == null) {
            log.info("Reservation {} has no payment to refund", reservation.getId());
            return;
        }

        try {
            RefundCreateParams refundParams = RefundCreateParams.builder()
                    .setPaymentIntent(reservation.getPaymentIntentId())
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .build();
            Refund refund = Refund.create(refundParams);

            log.info("Refund {} created for reservation {} (paymentIntent={}), amount refunded: {}",
                    refund.getId(), reservation.getId(), reservation.getPaymentIntentId(),
                    refund.getAmount() / 100.0);
        } catch (StripeException e) {
            log.error("Failed to refund reservation {} via Stripe: {}", reservation.getId(), e.getMessage());
            // Ne pas relancer l'exception pour ne pas bloquer l'annulation du voyage
        }
    }
}