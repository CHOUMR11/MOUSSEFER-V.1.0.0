package com.moussefer.voyage.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations_voyage")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationVoyage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "voyage_id", nullable = false)
    private String voyageId;

    @Column(name = "passenger_id", nullable = false)
    private String passengerId;

    @Column(name = "seats_reserved", nullable = false)
    private int seatsReserved;

    @Column(name = "total_price")
    private Double totalPrice;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(name = "invoice_url")
    private String invoiceUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationVoyageStatus status = ReservationVoyageStatus.PENDING_ORGANIZER;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}