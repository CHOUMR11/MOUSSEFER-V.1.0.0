package com.moussefer.trajet.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Government-regulated fare for a louage route.
 *
 * Prices on inter-city shared taxi routes in Tunisia are set by the
 * Ministry of Transport and the Chambre Syndicale des Louages, not by
 * individual drivers. Drivers cannot publish trajets at prices that
 * diverge from the regulated fare.
 *
 * Fares are imported in bulk by a SUPER_ADMIN or FINANCIAL_ADMIN from a
 * JSON or CSV file sourced from the ministry. Each entry is identified
 * by the (departureCity, arrivalCity) pair and carries metadata about
 * when the fare took effect and where it came from — useful for
 * compliance and historical traceability.
 *
 * On trajet publish, TrajetService looks up the regulated fare and
 * overrides any driver-supplied price with the official value. If no
 * regulated fare exists for a given route, the trajet is rejected.
 */
@Entity
@Table(name = "regulated_fares",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_route",
               columnNames = {"departure_city", "arrival_city"}),
       indexes = {
               @Index(name = "idx_departure_city", columnList = "departure_city"),
               @Index(name = "idx_arrival_city",   columnList = "arrival_city")
       })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RegulatedFare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "departure_city", nullable = false, length = 80)
    private String departureCity;

    @Column(name = "arrival_city", nullable = false, length = 80)
    private String arrivalCity;

    /**
     * The official price per seat in Tunisian Dinars.
     * precision=10, scale=2 keeps 8 digits before the decimal (more than enough)
     * and exactly 2 decimals — matches the trajet.pricePerSeat column.
     */
    @Column(name = "price_per_seat", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    /**
     * Approximate distance between the two cities, in kilometres.
     * Optional — useful for display and compliance cross-checks.
     */
    @Column(name = "distance_km", precision = 6, scale = 1)
    private BigDecimal distanceKm;

    /**
     * Date the fare became effective. A new import can update the price
     * for an existing route — we keep the most recent effective date.
     */
    @Column(name = "effective_date")
    private java.time.LocalDate effectiveDate;

    /**
     * Source of the data — typically "MINISTERE_TRANSPORT" or
     * "CHAMBRE_SYNDICALE_LOUAGE" or a reference number from the
     * official circular.
     */
    @Column(name = "source", length = 100)
    private String source;

    /**
     * Whether this fare is currently enforced. An admin can soft-disable
     * a fare (e.g. during a dispute or pending clarification) without
     * deleting the row.
     */
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "imported_by", length = 100)
    private String importedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
