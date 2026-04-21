package com.moussefer.voyage.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CreateVoyageRequest {
    @NotBlank private String departureCity;
    @NotBlank private String arrivalCity;
    @NotNull @Future private LocalDateTime departureDate;
    @Min(1) private int totalSeats;
    @Positive private Double pricePerSeat;
}