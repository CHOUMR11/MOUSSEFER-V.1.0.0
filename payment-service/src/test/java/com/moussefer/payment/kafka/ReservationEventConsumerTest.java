package com.moussefer.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.entity.PaymentStatus;
import com.moussefer.payment.repository.PaymentRepository;
import com.moussefer.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests du consumer Kafka d'auto-refund (V22).
 *
 * Garanties critiques à vérifier :
 *   1. Reservation escalated/refused/cancelled + Payment SUCCEEDED → refund déclenché
 *   2. Idempotency : Payment déjà REFUNDED → no-op (skip silencieux)
 *   3. Aucun paiement (passager n'a jamais payé) → no-op
 *   4. Payment PENDING ou FAILED → no-op (rien à rembourser)
 *   5. Le consumer ne crash JAMAIS le service même si l'event est mal formé
 *      (les exceptions sont catchées et loggées)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationEventConsumer — Auto-refund Kafka V22")
class ReservationEventConsumerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ReservationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ReservationEventConsumer(paymentRepository, paymentService, objectMapper);
    }

    @Test
    @DisplayName("reservation.escalated + Payment SUCCEEDED → refundPayment appelé")
    void onEscalated_paymentSucceeded_triggersRefund() {
        Payment paid = Payment.builder()
                .id("pay-1").reservationId("res-1")
                .amount(new BigDecimal("45.00"))
                .status(PaymentStatus.SUCCEEDED)
                .build();
        when(paymentRepository.findByReservationId("res-1")).thenReturn(Optional.of(paid));

        consumer.onEscalated("{\"reservationId\":\"res-1\",\"reason\":\"timeout\"}");

        verify(paymentService).refundPayment(
                eq("pay-1"),
                eq("system:escalation_timeout"),
                eq(new BigDecimal("45.00")));
    }

    @Test
    @DisplayName("Idempotency : Payment déjà REFUNDED → AUCUN refund (pas de double remboursement)")
    void onEscalated_alreadyRefunded_skipsSilently() {
        Payment refunded = Payment.builder()
                .id("pay-2").reservationId("res-2")
                .amount(new BigDecimal("45.00"))
                .status(PaymentStatus.REFUNDED)
                .build();
        when(paymentRepository.findByReservationId("res-2")).thenReturn(Optional.of(refunded));

        consumer.onEscalated("{\"reservationId\":\"res-2\"}");

        verify(paymentService, never()).refundPayment(any(), any(), any());
    }

    @Test
    @DisplayName("Aucun paiement existant (passager n'a jamais payé) → no-op")
    void onEscalated_noPayment_noOp() {
        when(paymentRepository.findByReservationId("res-3")).thenReturn(Optional.empty());

        consumer.onEscalated("{\"reservationId\":\"res-3\"}");

        verify(paymentService, never()).refundPayment(any(), any(), any());
    }

    @Test
    @DisplayName("Payment PENDING → no-op (rien à rembourser, paiement pas encore confirmé)")
    void onEscalated_paymentPending_noOp() {
        Payment pending = Payment.builder()
                .id("pay-4").reservationId("res-4")
                .amount(new BigDecimal("45.00"))
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findByReservationId("res-4")).thenReturn(Optional.of(pending));

        consumer.onEscalated("{\"reservationId\":\"res-4\"}");

        verify(paymentService, never()).refundPayment(any(), any(), any());
    }

    @Test
    @DisplayName("Payment FAILED → no-op (le paiement a échoué, rien à rembourser)")
    void onEscalated_paymentFailed_noOp() {
        Payment failed = Payment.builder()
                .id("pay-5").reservationId("res-5")
                .amount(new BigDecimal("45.00"))
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findByReservationId("res-5")).thenReturn(Optional.of(failed));

        consumer.onEscalated("{\"reservationId\":\"res-5\"}");

        verify(paymentService, never()).refundPayment(any(), any(), any());
    }

    @Test
    @DisplayName("reservation.refused + paid → refund avec trigger 'driver_refused'")
    void onRefused_paymentSucceeded_triggersRefundWithCorrectTrigger() {
        Payment paid = Payment.builder()
                .id("pay-6").reservationId("res-6")
                .amount(new BigDecimal("30.00"))
                .status(PaymentStatus.SUCCEEDED).build();
        when(paymentRepository.findByReservationId("res-6")).thenReturn(Optional.of(paid));

        consumer.onRefused("{\"reservationId\":\"res-6\",\"reason\":\"driver unavailable\"}");

        verify(paymentService).refundPayment(
                eq("pay-6"),
                eq("system:driver_refused"),
                eq(new BigDecimal("30.00")));
    }

    @Test
    @DisplayName("reservation.cancelled standard (passager annulation) → refund avec trigger 'cancellation'")
    void onCancelled_normal_triggersRefundWithCancellation() {
        Payment paid = Payment.builder()
                .id("pay-8").reservationId("res-8")
                .amount(new BigDecimal("25.00"))
                .status(PaymentStatus.SUCCEEDED).build();
        when(paymentRepository.findByReservationId("res-8")).thenReturn(Optional.of(paid));

        consumer.onCancelled("{\"reservationId\":\"res-8\"}");

        verify(paymentService).refundPayment(
                eq("pay-8"),
                eq("system:cancellation"),
                eq(new BigDecimal("25.00")));
    }

    @Test
    @DisplayName("Event sans reservationId → log warn + skip (pas de crash du consumer)")
    void onEscalated_missingReservationId_logsAndSkips() {
        // Event mal formé — pas de reservationId
        consumer.onEscalated("{\"reason\":\"timeout\"}");

        // Aucun appel au repository, aucun refund
        verify(paymentRepository, never()).findByReservationId(any());
        verify(paymentService, never()).refundPayment(any(), any(), any());
    }

    @Test
    @DisplayName("JSON malformé → catch silencieux (le consumer ne meurt jamais)")
    void onEscalated_malformedJson_catchesAndLogs() {
        // JSON cassé — le consumer ne doit pas crasher
        consumer.onEscalated("{not valid json}");

        // Aucun side-effect
        verify(paymentRepository, never()).findByReservationId(any());
        verify(paymentService, never()).refundPayment(any(), any(), any());
    }

    @Test
    @DisplayName("Exception de refundPayment → catchée, ne propage pas (consumer reste vivant)")
    void onEscalated_refundFails_consumerStaysAlive() {
        Payment paid = Payment.builder()
                .id("pay-fail").reservationId("res-fail")
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.SUCCEEDED).build();
        when(paymentRepository.findByReservationId("res-fail")).thenReturn(Optional.of(paid));
        // Stripe down, refund échoue
        doThrow(new RuntimeException("Stripe API unreachable"))
                .when(paymentService).refundPayment(any(), any(), any());

        // Le consumer ne doit PAS lever d'exception — sinon Kafka recommence en boucle
        consumer.onEscalated("{\"reservationId\":\"res-fail\"}");

        // L'appel a bien été tenté
        verify(paymentService).refundPayment(any(), any(), any());
    }
}
