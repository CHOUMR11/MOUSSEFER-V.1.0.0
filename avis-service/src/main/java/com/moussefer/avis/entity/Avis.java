package com.moussefer.avis.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "avis", indexes = {
        @Index(name = "idx_driver_id", columnList = "driver_id"),
        @Index(name = "idx_passenger_trajet", columnList = "passenger_id, trajet_id"),
        @Index(name = "idx_reservation_id", columnList = "reservation_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Avis {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "passenger_id", nullable = false)
    private String passengerId;

    @Column(name = "trajet_id", nullable = false)
    private String trajetId;

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}