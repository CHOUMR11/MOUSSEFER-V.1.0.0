package com.moussefer.avis.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AvisRequest {

    @NotBlank
    private String driverId;

    @NotBlank
    private String trajetId;

    private String reservationId;

    @Min(1)
    @Max(5)
    private int rating;

    @Size(max = 1000)
    private String comment;
}