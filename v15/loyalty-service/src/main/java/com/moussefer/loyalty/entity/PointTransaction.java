package com.moussefer.loyalty.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "points_delta", nullable = false)
    private int pointsDelta; // positive = earn, negative = redeem

    @Column(name = "reason")
    private String reason; // TRIP_COMPLETED, REDEEMED, BONUS, ADJUSTMENT

    @Column(name = "reference_id")
    private String referenceId; // reservation/trajet ID

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
