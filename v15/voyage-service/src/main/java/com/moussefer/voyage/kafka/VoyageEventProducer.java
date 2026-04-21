package com.moussefer.voyage.kafka;

import com.moussefer.voyage.entity.ReservationVoyage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class VoyageEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Émis lorsque l'organisateur accepte une réservation (avant paiement).
     */
    public void sendReservationConfirmed(ReservationVoyage reservation) {
        kafkaTemplate.send("voyage.reservation.confirmed", reservation.getId(),
                Map.of(
                        "reservationId", reservation.getId(),
                        "voyageId", reservation.getVoyageId(),
                        "passengerId", reservation.getPassengerId(),
                        "seats", reservation.getSeatsReserved()
                ));
        log.info("Published voyage.reservation.confirmed: {}", reservation.getId());
    }

    /**
     * Émis après confirmation du paiement (webhook Stripe).
     */
    public void sendPaymentConfirmed(ReservationVoyage reservation, String departureCity,
                                     String arrivalCity, String departureDate) {
        kafkaTemplate.send("voyage.payment.confirmed", reservation.getId(),
                Map.of(
                        "reservationId", reservation.getId(),
                        "voyageId", reservation.getVoyageId(),
                        "passengerId", reservation.getPassengerId(),
                        "seats", reservation.getSeatsReserved(),
                        "totalPrice", reservation.getTotalPrice() != null ? reservation.getTotalPrice() : 0,
                        "departureCity", departureCity,
                        "arrivalCity", arrivalCity,
                        "departureDate", departureDate
                ));
        log.info("Published voyage.payment.confirmed: {}", reservation.getId());
    }

    /**
     * Émis lorsqu'un voyage est annulé et qu'un remboursement est nécessaire.
     */
    public void sendVoyageCancelledRefund(ReservationVoyage reservation, String voyageId) {
        kafkaTemplate.send("voyage.cancelled.refund", reservation.getId(),
                Map.of(
                        "reservationId", reservation.getId(),
                        "voyageId", voyageId,
                        "passengerId", reservation.getPassengerId(),
                        "seatsReserved", reservation.getSeatsReserved(),
                        "totalPrice", reservation.getTotalPrice() != null ? reservation.getTotalPrice() : 0,
                        "paymentIntentId", reservation.getPaymentIntentId() != null ? reservation.getPaymentIntentId() : ""
                ));
        log.info("Published voyage.cancelled.refund for reservation {}", reservation.getId());
    }
}