package com.moussefer.demande.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ConvertDemandeRequest {
    private LocalDateTime departureDate;
    private BigDecimal pricePerSeat;
}