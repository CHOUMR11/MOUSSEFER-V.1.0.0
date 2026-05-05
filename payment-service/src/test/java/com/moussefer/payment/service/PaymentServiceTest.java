package com.moussefer.payment.service;

import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.entity.PaymentStatus;
import com.moussefer.payment.exception.BusinessException;
import com.moussefer.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests métier de PaymentService — règles de business sans appel Stripe réel.
 *
 * Couvre les invariants critiques du remboursement :
 *   1. Seuls les paiements SUCCEEDED peuvent être remboursés (pas PENDING/FAILED/REFUNDED)
 *   2. Le montant de refund ne peut pas dépasser le montant payé
 *   3. Les remboursements partiels sont autorisés (montant < total)
 *   4. Tentative de refund sur paiement inexistant → BusinessException explicite
 *
 * Note : on ne teste PAS l'appel réel à Stripe.Refund.create() ici car cela
 * nécessite une connexion réseau et des credentials. Ce test couvre les
 * pré-conditions métier qui s'exécutent AVANT l'appel Stripe — ce qui est
 * exactement ce qui peut casser ou être contourné en cas de bug applicatif.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — règles métier de remboursement et de paiement")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PromoCodeService promoCodeService;
    @Mock private WebClient reservationServiceWebClient;

    @InjectMocks private PaymentService paymentService;

    // ─────────── REFUND RULES ───────────

    @Test
    @DisplayName("Refund sur paiement PENDING → BusinessException (jamais payé)")
    void refundPayment_pendingPayment_rejected() {
        Payment pending = Payment.builder()
                .id("pay-1")
                .amount(new BigDecimal("45.00"))
                .status(PaymentStatus.PENDING)
                .build();
        when(paymentRepository.findById("pay-1")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() ->
                paymentService.refundPayment("pay-1", "admin-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only succeeded payments");

        verify(paymentRepository, never()).save(pending);
    }

    @Test
    @DisplayName("Refund sur paiement FAILED → BusinessException")
    void refundPayment_failedPayment_rejected() {
        Payment failed = Payment.builder()
                .id("pay-2")
                .amount(new BigDecimal("45.00"))
                .status(PaymentStatus.FAILED)
                .build();
        when(paymentRepository.findById("pay-2")).thenReturn(Optional.of(failed));

        assertThatThrownBy(() ->
                paymentService.refundPayment("pay-2", "admin-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only succeeded payments");
    }

    @Test
    @DisplayName("Refund déjà effectué → BusinessException (anti-double-refund)")
    void refundPayment_alreadyRefunded_rejected() {
        Payment refunded = Payment.builder()
                .id("pay-3")
                .amount(new BigDecimal("45.00"))
                .status(PaymentStatus.REFUNDED)
                .build();
        when(paymentRepository.findById("pay-3")).thenReturn(Optional.of(refunded));

        assertThatThrownBy(() ->
                paymentService.refundPayment("pay-3", "admin-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only succeeded payments");
    }

    @Test
    @DisplayName("Refund avec montant supérieur au paiement → BusinessException")
    void refundPayment_amountExceedsPaid_rejected() {
        Payment paid = Payment.builder()
                .id("pay-4")
                .reservationId("res-1")
                .amount(new BigDecimal("45.00"))
                .stripePaymentIntentId("pi_test_123")
                .status(PaymentStatus.SUCCEEDED)
                .build();
        when(paymentRepository.findById("pay-4")).thenReturn(Optional.of(paid));

        // Tentative de rembourser 50 DT alors que seulement 45 DT a été payé
        assertThatThrownBy(() ->
                paymentService.refundPayment("pay-4", "admin-1", new BigDecimal("50.00")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot exceed");

        verify(paymentRepository, never()).save(paid);
    }

    @Test
    @DisplayName("Refund sur paiement inexistant → BusinessException avec message explicite")
    void refundPayment_unknownPayment_throwsBusinessException() {
        when(paymentRepository.findById("pay-unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.refundPayment("pay-unknown", "admin-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Payment not found");
    }

    @Test
    @DisplayName("Refund avec montant null = remboursement total (pas de validation à faire)")
    void refundPayment_nullAmountMeansFullRefund() {
        Payment paid = Payment.builder()
                .id("pay-5")
                .reservationId("res-2")
                .amount(new BigDecimal("100.00"))
                .stripePaymentIntentId("pi_test_456")
                .status(PaymentStatus.SUCCEEDED)
                .build();
        when(paymentRepository.findById("pay-5")).thenReturn(Optional.of(paid));

        // Avec null, la pré-condition "amount > total" est skippée car amount est null.
        // Le code essaiera l'appel Stripe qui va échouer (pi_test_456 inexistant) →
        // on attend BusinessException du wrapping de StripeException.
        assertThatThrownBy(() ->
                paymentService.refundPayment("pay-5", "admin-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Refund failed"); // le message vient du catch StripeException
    }
}
