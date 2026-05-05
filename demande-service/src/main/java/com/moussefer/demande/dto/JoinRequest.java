package com.moussefer.demande.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JoinRequest {
    @NotNull
    private String demandeId;

    @Min(1)
    private int seatsReserved = 1;
}