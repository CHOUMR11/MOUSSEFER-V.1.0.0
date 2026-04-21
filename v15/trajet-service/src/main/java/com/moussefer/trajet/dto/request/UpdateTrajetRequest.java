package com.moussefer.trajet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UpdateTrajetRequest {
    @DecimalMin(value = "0.1") @DecimalMax(value = "9999.99")
    private BigDecimal pricePerSeat;

    @Size(max = 500)
    private String vehicleDescription;
}