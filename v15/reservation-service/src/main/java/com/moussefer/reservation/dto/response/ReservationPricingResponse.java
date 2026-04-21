package com.moussefer.reservation.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class ReservationPricingResponse {
    private String reservationId;
    private String passengerId;
    private String status;
    private BigDecimal totalPrice;
}