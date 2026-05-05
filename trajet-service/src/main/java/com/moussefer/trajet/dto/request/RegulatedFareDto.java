package com.moussefer.trajet.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input DTO for a regulated fare — used by both admin CRUD endpoints
 * and the bulk JSON import endpoint.
 *
 * Field names match the JSON schema an admin would produce from the
 * Ministry of Transport source data.
 */
@Data
@NoArgsConstructor
public class RegulatedFareDto {

    @JsonProperty("departureCity")
    private String departureCity;

    @JsonProperty("arrivalCity")
    private String arrivalCity;

    @JsonProperty("pricePerSeat")
    private BigDecimal pricePerSeat;

    @JsonProperty("distanceKm")
    private BigDecimal distanceKm;

    @JsonProperty("effectiveDate")
    private LocalDate effectiveDate;

    @JsonProperty("source")
    private String source;

    /**
     * Nullable — if omitted, defaults to true on upsert.
     */
    @JsonProperty("active")
    private Boolean active;
}
