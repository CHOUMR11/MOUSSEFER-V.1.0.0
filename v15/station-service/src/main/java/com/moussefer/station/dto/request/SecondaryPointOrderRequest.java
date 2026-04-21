package com.moussefer.station.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** US-115: Request body for PATCH /stations/secondary-points/{pointId} */
@Data
public class SecondaryPointOrderRequest {

    @NotNull(message = "displayOrder is required")
    @Min(value = 0, message = "displayOrder must be >= 0")
    private Integer displayOrder;
}
