package com.moussefer.demande.dto;

import com.moussefer.demande.entity.VehicleType;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UpdateDemandeRequest {

    private String departureCity;
    private String arrivalCity;
    private LocalDate requestedDate;
    private VehicleType vehicleType;

    @Min(1)
    private Integer seuilPersonnalise;
}