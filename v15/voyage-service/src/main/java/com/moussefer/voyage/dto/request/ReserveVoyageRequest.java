package com.moussefer.voyage.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReserveVoyageRequest {
    @NotBlank private String voyageId;
    @NotNull @Min(1) private Integer seats;
}