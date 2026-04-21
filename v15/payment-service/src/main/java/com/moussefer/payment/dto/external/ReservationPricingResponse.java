package com.moussefer.payment.dto.external;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReservationPricingResponse {
    private String reservationId;
    private String passengerId;
    private String status;
    private BigDecimal totalPrice;
}