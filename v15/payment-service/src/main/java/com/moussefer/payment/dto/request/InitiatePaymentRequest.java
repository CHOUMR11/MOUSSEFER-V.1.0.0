package com.moussefer.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InitiatePaymentRequest {

    @NotBlank
    private String reservationId;

    @NotBlank
    private String driverId;

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String currency;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String cancelUrl;

    private String promoCode;
}