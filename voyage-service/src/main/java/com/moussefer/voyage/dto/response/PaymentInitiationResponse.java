package com.moussefer.voyage.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentInitiationResponse {
    private String paymentId;
    private String clientSecret;
    private Long amount;
    private String currency;
}