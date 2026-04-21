package com.moussefer.reservation.dto.external;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TrajetInfoResponse {
    private String id;
    private String driverId;
    private BigDecimal pricePerSeat;
    private String departureCity;
    private String arrivalCity;
    private int availableSeats;
    private String status;
    private LocalDateTime departureDate;
}