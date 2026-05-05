package com.moussefer.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "alert_subscriptions", indexes = {
        @Index(name = "idx_alert_route", columnList = "departure_city, arrival_city"),
        @Index(name = "idx_alert_user", columnList = "user_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AlertSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "departure_city", nullable = false)
    private String departureCity;

    @Column(name = "arrival_city", nullable = false)
    private String arrivalCity;

    @Column(name = "desired_date")
    private LocalDate desiredDate;

    @Column(name = "min_seats")
    @Builder.Default
    private int minSeats = 1;

    @Column(name = "active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "notified")
    @Builder.Default
    private boolean notified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
