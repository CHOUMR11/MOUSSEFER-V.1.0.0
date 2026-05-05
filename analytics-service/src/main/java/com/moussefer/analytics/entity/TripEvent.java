package com.moussefer.analytics.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "trip_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TripEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "event_type", nullable = false)
    private String eventType; // BOOKED, CANCELLED, COMPLETED

    @Column(name = "trajet_id")
    private String trajetId;

    @Column(name = "reservation_id")
    private String reservationId;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "driver_id")
    private String driverId;

    @Column(name = "origin_city")
    private String originCity;

    @Column(name = "destination_city")
    private String destinationCity;

    @Column(name = "revenue")
    private Double revenue;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;
}
