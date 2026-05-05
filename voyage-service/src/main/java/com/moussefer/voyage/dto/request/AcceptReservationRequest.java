package com.moussefer.voyage.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AcceptReservationRequest {
    @NotBlank private String reservationId;
}