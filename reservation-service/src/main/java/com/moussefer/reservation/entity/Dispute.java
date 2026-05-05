package com.moussefer.reservation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "disputes", indexes = {
        @Index(name = "idx_dispute_reservation", columnList = "reservation_id"),
        @Index(name = "idx_dispute_status", columnList = "status")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "reservation_id", nullable = false)
    private String reservationId;

    @Column(name = "reporter_id", nullable = false)
    private String reporterId;

    @Column(name = "reporter_role", nullable = false)
    private String reporterRole; // PASSENGER or DRIVER

    @Column(name = "reported_user_id", nullable = false)
    private String reportedUserId;

    @Column(nullable = false, length = 100)
    private String category; // PAYMENT, BEHAVIOR, NO_SHOW, VEHICLE_CONDITION, OTHER

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "admin_id")
    private String adminId;

    @Column(name = "resolution", length = 1000)
    private String resolution;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
