package com.moussefer.trajet.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SearchTrajetRequest {
    @NotBlank
    private String departureCity;

    @NotBlank
    private String arrivalCity;

    @Min(1)
    private int seatsNeeded = 1;

    private LocalDate date;

    // morning | afternoon | evening | null (any)
    private String timeOfDay;

    // Vehicle option filters (optional — filtered in-memory after DB query)
    private Boolean acceptsPets;
    private Boolean airConditioned;
    private Boolean allowsLargeBags;
    private Boolean directTrip;

    // Price range filters (optional)
    private BigDecimal priceMin;
    private BigDecimal priceMax;
}
