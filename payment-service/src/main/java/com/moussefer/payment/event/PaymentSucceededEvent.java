package com.moussefer.payment.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSucceededEvent {
    private String paymentId;
    private String reservationId;
    private String passengerId;
    private String driverId;
    private BigDecimal amount;
    private String currency;
    private String invoiceUrl;
}