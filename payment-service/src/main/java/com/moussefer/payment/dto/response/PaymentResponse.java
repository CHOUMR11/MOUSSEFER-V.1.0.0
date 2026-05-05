package com.moussefer.payment.dto.response;

import com.moussefer.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private String paymentId;
    private String reservationId;
    private String passengerId;      // ✅ Ajout
    private String driverId;         // ✅ Ajout
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String clientSecret;
    private String invoiceUrl;
    private String promoCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}