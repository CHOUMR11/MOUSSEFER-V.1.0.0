package com.moussefer.reservation.dto.response;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ReservationResponse {
    private String id;
    private String trajetId;
    private String passengerId;
    private String driverId;
    private int seatsReserved;
    private BigDecimal totalPrice;
    private String status;
    private String refusalReason;
    private LocalDateTime driverResponseDeadline;
    private LocalDateTime confirmedAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}