package com.moussefer.demande.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class DemandeSearchRequest {
    @NotBlank
    private String departureCity;

    @NotBlank
    private String arrivalCity;

    private LocalDate requestedDate;
}