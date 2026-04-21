package com.moussefer.trajet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trajets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trajet_route_date_priority",
                columnNames = {"departure_city", "arrival_city", "departure_date", "priority_order"}
        ),
        indexes = {
                @Index(name = "idx_driver", columnList = "driver_id"),
                @Index(name = "idx_route", columnList = "departure_city, arrival_city"),
                @Index(name = "idx_date", columnList = "departure_date"),
                @Index(name = "idx_status", columnList = "status"),
                @Index(name = "idx_priority", columnList = "departure_city, arrival_city, departure_date, priority_order")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trajet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(name = "departure_city", nullable = false, length = 100)
    private String departureCity;

    @Column(name = "arrival_city", nullable = false, length = 100)
    private String arrivalCity;

    @Column(name = "departure_date", nullable = false)
    private LocalDateTime departureDate;

    @Column(name = "total_seats", nullable = false)
    private int totalSeats;

    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    // ✅ Nouveau champ : places réservées temporairement (en attente de paiement)
    @Column(name = "reserved_seats", nullable = false)
    @Builder.Default
    private int reservedSeats = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TrajetStatus status = TrajetStatus.ACTIVE;

    @Column(name = "priority_order", nullable = false)
    private int priorityOrder;

    @Version
    @Column(nullable = false)
    private Long version;

    private boolean acceptsPets;
    private boolean allowsLargeBags;
    private boolean airConditioned;
    private boolean hasIntermediateStops;
    private boolean directTrip;

    @Column(name = "price_per_seat", precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(length = 500)
    private String notes;

    @Column(name = "departed_at")
    private LocalDateTime departedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}