package com.moussefer.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "promo_codes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Column(nullable = false)
    private BigDecimal discountValue;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "max_uses")
    private Integer maxUses;

    @Column(name = "used_count")
    @Builder.Default
    private int usedCount = 0;

    @Column(name = "min_amount")
    private BigDecimal minAmount;

    @Column(name = "applicable_to")
    private String applicableTo;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isValid() {
        if (!active) return false;
        LocalDateTime now = LocalDateTime.now();
        if (validFrom != null && now.isBefore(validFrom)) return false;
        if (validUntil != null && now.isAfter(validUntil)) return false;
        if (maxUses != null && usedCount >= maxUses) return false;
        return true;
    }

    public BigDecimal applyDiscount(BigDecimal amount) {
        BigDecimal discounted;
        if (discountType == DiscountType.PERCENTAGE) {
            BigDecimal percent = discountValue.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
            discounted = amount.multiply(BigDecimal.ONE.subtract(percent));
        } else {
            discounted = amount.subtract(discountValue);
        }
        return discounted.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_EVEN);
    }
}