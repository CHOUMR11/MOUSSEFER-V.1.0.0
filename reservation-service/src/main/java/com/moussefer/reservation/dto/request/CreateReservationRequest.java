package com.moussefer.reservation.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateReservationRequest {

    @NotBlank
    private String trajetId;

    @Min(1)
    private int seatsReserved;

   // private Double totalPrice;  optionnel, peut être calculé par le service
}