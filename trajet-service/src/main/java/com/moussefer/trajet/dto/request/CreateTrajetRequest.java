package com.moussefer.trajet.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Demande de publication d'un trajet de louage.
 *
 * Note métier : la capacité d'un louage est fixée à 8 places par la
 * réglementation tunisienne (Ministère du Transport). Le chauffeur ne
 * choisit donc PAS le nombre de places — c'est une constante métier
 * appliquée côté serveur (TrajetService.LOUAGE_SEATS).
 */
@Data
public class CreateTrajetRequest {
    @NotBlank private String departureCity;
    @NotBlank private String arrivalCity;
    @NotNull @Future private LocalDateTime departureDate;

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