package com.moussefer.voyage.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UpdateVoyageRequest {

    @Size(max = 100, message = "Departure city must not exceed 100 characters")
    private String departureCity;

    @Size(max = 100, message = "Arrival city must not exceed 100 characters")
    private String arrivalCity;

    @Future(message = "Departure date must be in the future")
    private LocalDateTime departureDate;

    @Min(value = 1, message = "Total seats must be at least 1")
    @Max(value = 100, message = "Total seats cannot exceed 100")
    private Integer totalSeats;

    @Positive(message = "Price per seat must be positive")
    @DecimalMax(value = "9999.99", message = "Price cannot exceed 9999.99")
    private Double pricePerSeat;
}
