package com.moussefer.payment.service;

import com.moussefer.payment.dto.external.ReservationPricingResponse;
import com.moussefer.payment.dto.request.InitiatePaymentRequest;
import com.moussefer.payment.dto.response.PaymentResponse;
import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.entity.PaymentStatus;
import com.moussefer.payment.exception.BusinessException;
import com.moussefer.payment.repository.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PromoCodeService promoCodeService;
    private final WebClient reservationServiceWebClient;

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.10");

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${internal.api-key:dev-internal-key}")
    private String internalApiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    @Transactional
    public PaymentResponse initiatePayment(InitiatePaymentRequest request, String passengerId, String driverId) {
        if (paymentRepository.findByReservationId(request.getReservationId()).isPresent()) {
            throw new BusinessException("Payment already initiated for this reservation");
        }

        // SERVER-SIDE VERIFICATION: fetch real price and status from reservation-service
        ReservationPricingResponse pricing = reservationServiceWebClient.get()
                .uri("/api/v1/reservations/internal/{id}/pricing?passengerId={pid}",
                        request.getReservationId(), passengerId)
                .header("X-Internal-Secret", internalApiKey)
                .retrieve()
                .bodyToMono(ReservationPricingResponse.class)
                .block(java.time.Duration.ofSeconds(5));

        if (pricing == null) {
            throw new BusinessException("Reservation not found: " + request.getReservationId());
        }
        if (!"ACCEPTED".equals(pricing.getStatus()) && !"PAYMENT_PENDING".equals(pricing.getStatus())) {
            throw new BusinessException("Reservation not eligible for payment (status: " + pricing.getStatus() + ")");
        }
        if (!passengerId.equals(pricing.getPassengerId())) {
            throw new BusinessException("Not your reservation");
        }

        // Use server-side price, not client-provided amount
        BigDecimal originalAmount = pricing.getTotalPrice().setScale(2, RoundingMode.HALF_EVEN);
        if (originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Invalid reservation price");
        }
        BigDecimal finalAmount = originalAmount;
        String appliedPromoCode = null;

        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            var validation = promoCodeService.validatePromoCode(request.getPromoCode(), originalAmount, "TRAJET");
            if (!validation.isValid()) {
                throw new BusinessException(validation.getMessage());
            }
            finalAmount = validation.getFinalAmount();
            appliedPromoCode = request.getPromoCode();
        }

        long amountInCents = finalAmount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_EVEN).longValueExact();

        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(request.getCurrency().toLowerCase())
                .setDescription("Réservation Moussefer: " + request.getReservationId())
                .putMetadata("reservationId", request.getReservationId())
                .putMetadata("passengerId", passengerId)
                .putMetadata("driverId", driverId)
                .putMetadata("originalAmount", originalAmount.toString());
        if (appliedPromoCode != null) {
            paramsBuilder.putMetadata("promoCode", appliedPromoCode);
        }

        PaymentIntent intent;
        try {
            intent = PaymentIntent.create(paramsBuilder.build());
        } catch (StripeException e) {
            log.error("Stripe PaymentIntent creation failed: {}", e.getMessage());
            throw new BusinessException("Payment initiation failed: " + e.getMessage());
        }

        BigDecimal commission = finalAmount.multiply(COMMISSION_RATE).setScale(2, java.math.RoundingMode.HALF_EVEN);
        BigDecimal driverPayout = finalAmount.subtract(commission);

        Payment payment = Payment.builder()
                .reservationId(request.getReservationId())
                .passengerId(passengerId)
                .driverId(driverId)
                .amount(finalAmount)
                .currency(request.getCurrency())
                .stripePaymentIntentId(intent.getId())
                .status(PaymentStatus.PENDING)
                .promoCode(appliedPromoCode)
                .platformCommission(commission)
                .driverPayout(driverPayout)
                .build();
        payment = paymentRepository.save(payment);

        // Transition reservation ACCEPTED → PAYMENT_PENDING (server-side state machine)
        try {
            reservationServiceWebClient.patch()
                    .uri("/api/v1/reservations/internal/{id}/payment-initiated", request.getReservationId())
                    .header("X-Internal-Secret", internalApiKey)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(java.time.Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("Failed to transition reservation {} to PAYMENT_PENDING: {}", request.getReservationId(), e.getMessage());
            // Non-blocking: Stripe PaymentIntent already created, webhook will confirm eventually
        }

        // Code promo marked as used ONLY after successful payment (in StripeWebhookService)
        // This prevents marking it used if the payment is abandoned.

        log.info("Payment initiated: id={}, reservationId={}, originalAmount={}, finalAmount={}",
                payment.getId(), request.getReservationId(), originalAmount, finalAmount);

        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .reservationId(payment.getReservationId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .clientSecret(intent.getClientSecret())
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByReservation(String reservationId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException("Payment not found for reservation: " + reservationId));
        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByPassengerId(String passengerId, Pageable pageable) {
        return paymentRepository.findByPassengerIdOrderByCreatedAtDesc(passengerId, pageable)
                .map(this::toPaymentResponse);
    }

    @Transactional
    public PaymentResponse refundPayment(String paymentId, String adminId, BigDecimal amount) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException("Payment not found: " + paymentId));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BusinessException("Only succeeded payments can be refunded");
        }
        if (amount != null && amount.compareTo(payment.getAmount()) > 0) {
            throw new BusinessException("Refund amount cannot exceed payment amount");
        }

        try {
            long amountInCents = (amount != null ? amount : payment.getAmount())
                    .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .setAmount(amountInCents)
                    .build();
            com.stripe.model.Refund refund = com.stripe.model.Refund.create(params);
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            log.info("Payment {} refunded by admin {}, refundId={}", paymentId, adminId, refund.getId());
        } catch (StripeException e) {
            log.error("Stripe refund failed for payment {}: {}", paymentId, e.getMessage());
            throw new BusinessException("Refund failed: " + e.getMessage());
        }
        return getPaymentByReservation(payment.getReservationId());
    }

    private PaymentResponse toPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .reservationId(payment.getReservationId())
                .passengerId(payment.getPassengerId())
                .driverId(payment.getDriverId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .invoiceUrl(payment.getInvoiceUrl())
                .promoCode(payment.getPromoCode())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    // ─── Invoice download ────────────────────────────────────────────
    @Transactional(readOnly = true)
    public org.springframework.http.ResponseEntity<byte[]> getInvoicePdf(String reservationId, String userId) {
        Payment payment = paymentRepository.findByReservationId(reservationId)
                .orElseThrow(() -> new BusinessException("Payment not found for reservation: " + reservationId));
        if (!payment.getPassengerId().equals(userId) && !payment.getDriverId().equals(userId)) {
            throw new BusinessException("Not authorized to download this invoice");
        }
        if (payment.getInvoiceUrl() == null || payment.getInvoiceUrl().isBlank()) {
            throw new BusinessException("Invoice not yet generated");
        }
        // Redirect to MinIO presigned URL
        return org.springframework.http.ResponseEntity.status(302)
                .header("Location", payment.getInvoiceUrl())
                .build();
    }

    // ─── CSV export ──────────────────────────────────────────────────
    public byte[] exportTransactionsCsv(String status, String fromDate, String toDate) {
        LocalDateTime from = fromDate != null ? LocalDate.parse(fromDate).atStartOfDay() : LocalDateTime.now().minusMonths(1);
        LocalDateTime to = toDate != null ? LocalDate.parse(toDate).plusDays(1).atStartOfDay() : LocalDateTime.now();
        java.util.List<Payment> payments;
        if (status != null) {
            payments = paymentRepository.findByStatusAndCreatedAtBetween(PaymentStatus.valueOf(status.toUpperCase()), from, to);
        } else {
            payments = paymentRepository.findByCreatedAtBetween(from, to);
        }
        StringBuilder csv = new StringBuilder();
        csv.append("id,reservationId,passengerId,driverId,amount,currency,status,commission,driverPayout,promoCode,createdAt\n");
        for (Payment p : payments) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    p.getId(), p.getReservationId(), p.getPassengerId(), p.getDriverId(),
                    p.getAmount(), p.getCurrency(), p.getStatus(),
                    p.getPlatformCommission() != null ? p.getPlatformCommission() : "",
                    p.getDriverPayout() != null ? p.getDriverPayout() : "",
                    p.getPromoCode() != null ? p.getPromoCode() : "",
                    p.getCreatedAt()));
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── Commission report ───────────────────────────────────────────
    public java.util.Map<String, Object> getCommissionReport(String fromDate, String toDate) {
        LocalDateTime from = fromDate != null ? LocalDate.parse(fromDate).atStartOfDay() : LocalDateTime.now().withDayOfMonth(1).toLocalDate().atStartOfDay();
        LocalDateTime to = toDate != null ? LocalDate.parse(toDate).plusDays(1).atStartOfDay() : LocalDateTime.now();
        BigDecimal totalRevenue = paymentRepository.sumRevenueInPeriod(from, to);
        BigDecimal totalCommissions = paymentRepository.sumCommissionsInPeriod(from, to);
        BigDecimal totalRefunds = paymentRepository.sumRefundsInPeriod(from, to);
        long totalTransactions = paymentRepository.countByStatus(PaymentStatus.SUCCEEDED);
        return java.util.Map.of(
                "period", java.util.Map.of("from", from.toString(), "to", to.toString()),
                "grossRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO,
                "platformCommissions", totalCommissions != null ? totalCommissions : BigDecimal.ZERO,
                "totalRefunds", totalRefunds != null ? totalRefunds : BigDecimal.ZERO,
                "netRevenue", (totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                        .subtract(totalRefunds != null ? totalRefunds : BigDecimal.ZERO),
                "totalSucceededTransactions", totalTransactions,
                "commissionRate", COMMISSION_RATE.multiply(BigDecimal.valueOf(100)) + "%"
        );
    }

    // ─── Driver payouts summary ──────────────────────────────────────
    public java.util.List<java.util.Map<String, Object>> getDriverPayoutsSummary() {
        java.util.List<Object[]> rows = paymentRepository.sumDriverPayouts();
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            result.add(java.util.Map.of(
                    "driverId", row[0],
                    "totalPayout", row[1] != null ? row[1] : BigDecimal.ZERO,
                    "tripsCompleted", row[2]
            ));
        }
        return result;
    }
}