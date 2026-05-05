package com.moussefer.loyalty.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RedeemRequest {
    @NotNull
    @Min(value = 1, message = "Must redeem at least 1 point")
    private Integer points;

    private String referenceId;
}
