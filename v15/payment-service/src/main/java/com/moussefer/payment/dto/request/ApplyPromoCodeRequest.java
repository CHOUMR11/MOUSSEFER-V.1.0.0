package com.moussefer.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyPromoCodeRequest {
    @NotBlank
    private String code;
}