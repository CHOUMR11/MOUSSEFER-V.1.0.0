package com.moussefer.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_reservation", columnList = "reservation_id"),
        @Index(name = "idx_passenger", columnList = "passenger_id"),
        @Index(name = "idx_stripe_intent", columnList = "stripe_payment_intent_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private String reservationId;

    @Column(name = "passenger_id", nullable = false)
    private String passengerId;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "promo_code", length = 50)
    private String promoCode;

    @Column(name = "platform_commission", precision = 10, scale = 2)
    private BigDecimal platformCommission;

    @Column(name = "driver_payout", precision = 10, scale = 2)
    private BigDecimal driverPayout;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}