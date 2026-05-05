package com.moussefer.reservation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_passenger", columnList = "passenger_id"),
        @Index(name = "idx_trajet",    columnList = "trajet_id"),
        @Index(name = "idx_driver",    columnList = "driver_id"),   // AJOUTÉ
        @Index(name = "idx_status",    columnList = "status"),
        @Index(name = "idx_pending",   columnList = "status, created_at"),
        @Index(name = "idx_deadline",  columnList = "driver_response_deadline")  // optionnel, utile pour les schedulers
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "trajet_id",    nullable = false) private String trajetId;
    @Column(name = "passenger_id", nullable = false) private String passengerId;
    @Column(name = "driver_id",    nullable = false) private String driverId;

    @Column(name = "seats_reserved", nullable = false) private int seatsReserved;
    @Column(name = "total_price", precision = 10, scale = 2) private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING_DRIVER;

    @Column(name = "refusal_reason", length = 300) private String refusalReason;

    @Column(name = "driver_response_deadline") private LocalDateTime driverResponseDeadline;
    @Column(name = "reminder_sent")   @Builder.Default private boolean reminderSent = false;
    @Column(name = "admin_notified")  @Builder.Default private boolean adminNotified = false;
    @Column(name = "escalated")       @Builder.Default private boolean escalated = false;

    @Column(name = "payment_intent_id", length = 200) private String paymentIntentId;
    @Column(name = "confirmed_at")   private LocalDateTime confirmedAt;
    @Column(name = "paid_at")        private LocalDateTime paidAt;
    @Column(name = "cancelled_at")   private LocalDateTime cancelledAt;

    @Column(name = "departure_date")
    private LocalDateTime departureDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")     private LocalDateTime updatedAt;
}