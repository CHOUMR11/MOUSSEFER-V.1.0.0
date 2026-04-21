package com.moussefer.payment.dto.request;

import com.moussefer.payment.entity.DiscountType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePromoCodeRequest {

    @NotBlank(message = "Code is required")
    @Size(min = 3, max = 50, message = "Code must be between 3 and 50 characters")
    private String code;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @DecimalMin(value = "0.01", message = "Discount value must be positive")
    private BigDecimal discountValue;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    @Min(value = 1, message = "Max uses must be at least 1")
    private Integer maxUses;

    @DecimalMin(value = "0.00", message = "Min amount must be non-negative")
    private BigDecimal minAmount;

    /** TRAJET, VOYAGE, ALL */
    private String applicableTo = "ALL";
}
