package com.moussefer.payment.controller;

import com.moussefer.payment.dto.request.InitiatePaymentRequest;
import com.moussefer.payment.dto.response.PaymentResponse;
import com.moussefer.payment.dto.response.PromoCodeValidationResponse;
import com.moussefer.payment.service.PaymentService;
import com.moussefer.payment.service.PromoCodeService;
import com.moussefer.payment.service.StripeWebhookService;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payments", description = "Payment management for passengers")
public class PaymentController {

    private final PaymentService paymentService;
    private final PromoCodeService promoCodeService;
    private final StripeWebhookService webhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate a payment (client side)")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @RequestHeader("X-User-Id") String passengerId,
            @Valid @RequestBody InitiatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.initiatePayment(request, passengerId));
    }

    @GetMapping("/my")
    @Operation(summary = "Payment history for the authenticated user")
    public ResponseEntity<Page<PaymentResponse>> getMyPayments(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(paymentService.getPaymentsByPassengerId(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/reservation/{reservationId}")
    @Operation(summary = "Get payment status by reservation")
    public ResponseEntity<PaymentResponse> getPaymentByReservation(@PathVariable String reservationId) {
        return ResponseEntity.ok(paymentService.getPaymentByReservation(reservationId));
    }

    @GetMapping("/validate-promo")
    @Operation(summary = "Validate a promotional code")
    public ResponseEntity<PromoCodeValidationResponse> validatePromoCode(
            @RequestParam String code,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "TRAJET") String reservationType) {
        return ResponseEntity.ok(promoCodeService.validatePromoCode(code, amount, reservationType));
    }

    @PostMapping("/webhook")
    @Operation(summary = "Stripe webhook endpoint")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload,
                                                      @RequestHeader("Stripe-Signature") String sigHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Stripe webhook secret not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            log.error("Webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }
        String eventId = event.getId();
        if ("payment_intent.succeeded".equals(event.getType())) {
            PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (intent != null) {
                webhookService.handlePaymentIntentSucceeded(eventId, intent);
            }
        } else if ("payment_intent.payment_failed".equals(event.getType())) {
            PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
            if (intent != null) {
                webhookService.handlePaymentIntentFailed(eventId, intent);
            }
        }
        return ResponseEntity.ok("Received");
    }

    @GetMapping("/invoice/{reservationId}")
    @Operation(summary = "Download invoice PDF for a reservation")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable String reservationId,
            @RequestHeader("X-User-Id") String userId) {
        return paymentService.getInvoicePdf(reservationId, userId);
    }
}