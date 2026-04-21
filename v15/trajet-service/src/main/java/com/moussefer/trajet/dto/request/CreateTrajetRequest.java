package com.moussefer.trajet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateTrajetRequest {
    @NotBlank private String departureCity;
    @NotBlank private String arrivalCity;
    @NotNull @Future private LocalDateTime departureDate;
    @Min(1) @Max(20) private int totalSeats;

    @NotNull(message = "Price per seat is required")
    @DecimalMin(value = "0.1", message = "Price must be greater than 0")
    @DecimalMax(value = "9999.99", message = "Price cannot exceed 9999.99")
    private BigDecimal pricePerSeat;

    private boolean acceptsPets;
    private boolean allowsLargeBags;
    private boolean airConditioned;
    private boolean hasIntermediateStops;
    private boolean directTrip;
    @Size(max = 500) private String notes;
}