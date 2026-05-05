package com.moussefer.demande.dto;

import com.moussefer.demande.entity.VehicleType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class DemandeCreationRequest {

    @NotBlank
    private String departureCity;

    @NotBlank
    private String arrivalCity;

    @NotNull
    private LocalDate requestedDate;

    @NotNull
    private VehicleType vehicleType;

    @Min(1)
    private Integer seuilPersonnalise;
}