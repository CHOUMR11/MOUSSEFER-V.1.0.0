package com.moussefer.loyalty.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "loyalty_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoyaltyAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "points")
    @Builder.Default
    private int points = 0;

    @Column(name = "total_earned")
    @Builder.Default
    private int totalEarned = 0;

    @Column(name = "total_redeemed")
    @Builder.Default
    private int totalRedeemed = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier")
    @Builder.Default
    private LoyaltyTier tier = LoyaltyTier.BRONZE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
