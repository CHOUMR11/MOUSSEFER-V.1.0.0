package com.moussefer.payment.dto.response;

import com.moussefer.payment.entity.DiscountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class PromoCodeValidationResponse {
    private boolean valid;
    private String code;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private String message;

    public static PromoCodeValidationResponse valid(String code, BigDecimal discountAmount, BigDecimal finalAmount,
                                                    DiscountType type, BigDecimal value) {
        return PromoCodeValidationResponse.builder()
                .valid(true)
                .code(code)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .discountType(type)
                .discountValue(value)
                .build();
    }

    public static PromoCodeValidationResponse invalid(String message) {
        return PromoCodeValidationResponse.builder()
                .valid(false)
                .message(message)
                .build();
    }
}