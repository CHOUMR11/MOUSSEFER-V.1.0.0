package com.moussefer.demande.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateTrajetFromDemandeRequest {
    private String departureCity;
    private String arrivalCity;
    private LocalDateTime departureDate;
    private int totalSeats;
    private BigDecimal pricePerSeat;
    private boolean acceptsPets = false;
    private boolean airConditioned = true;
    private boolean hasIntermediateStops = false;
}