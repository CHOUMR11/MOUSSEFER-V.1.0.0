package com.moussefer.payment.dto.response;

import com.moussefer.payment.entity.DiscountType;
import com.moussefer.payment.entity.PromoCode;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PromoCodeResponse {

    private String id;
    private String code;
    private DiscountType discountType;
    private BigDecimal discountValue;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Integer maxUses;
    private int usedCount;
    private BigDecimal minAmount;
    private String applicableTo;
    private boolean active;
    private boolean currentlyValid;
    private LocalDateTime createdAt;

    public static PromoCodeResponse from(PromoCode p) {
        return PromoCodeResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .discountType(p.getDiscountType())
                .discountValue(p.getDiscountValue())
                .validFrom(p.getValidFrom())
                .validUntil(p.getValidUntil())
                .maxUses(p.getMaxUses())
                .usedCount(p.getUsedCount())
                .minAmount(p.getMinAmount())
                .applicableTo(p.getApplicableTo())
                .active(p.isActive())
                .currentlyValid(p.isValid())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
