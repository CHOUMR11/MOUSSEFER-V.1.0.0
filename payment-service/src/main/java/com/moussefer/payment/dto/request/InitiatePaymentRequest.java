package com.moussefer.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InitiatePaymentRequest {

    @NotBlank
    private String reservationId;

    @NotBlank
    private String driverId;

    @NotBlank
    private String successUrl;

    @NotBlank
    private String cancelUrl;

    private String promoCode;
}