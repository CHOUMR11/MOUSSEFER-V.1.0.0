package com.moussefer.payment.dto.request;

import com.moussefer.payment.entity.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdatePromoCodeRequest {
    private DiscountType discountType;

    @DecimalMin(value = "0.01")
    private BigDecimal discountValue;

    private LocalDateTime validFrom;
    private LocalDateTime validUntil;

    @Min(1)
    private Integer maxUses;

    @DecimalMin(value = "0.00")
    private BigDecimal minAmount;

    private String applicableTo;
    private Boolean active;
}
