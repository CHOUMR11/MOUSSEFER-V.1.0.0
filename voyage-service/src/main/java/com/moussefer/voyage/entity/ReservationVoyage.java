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

    // ─── V21: "Hors Moussefer" booking source tracking ─────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "booking_source", nullable = false, length = 20)
    @Builder.Default
    private BookingSource bookingSource = BookingSource.PLATFORM;

    @Column(name = "manual_booking")
    @Builder.Default
    private boolean manualBooking = false;

    @Column(name = "manual_passenger_name", length = 100)
    private String manualPassengerName;

    @Column(name = "manual_passenger_phone", length = 20)
    private String manualPassengerPhone;

    /**
     * For manual bookings, tracks whether the full amount is paid
     * (PAID), partially paid (DEPOSIT), or not yet paid (UNPAID).
     * Matches the organizer finances view: Payés / Acomptes reçus / Non encaissé.
     */
    @Column(name = "payment_state", length = 20)
    private String paymentState;

    @Column(name = "deposit_amount")
    private Double depositAmount;

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