package com.moussefer.demande.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "demandes_collectives", indexes = {
        @Index(name = "idx_organisateur", columnList = "organisateur_id"),
        @Index(name = "idx_statut", columnList = "statut"),
        @Index(name = "idx_route_date", columnList = "ville_depart, ville_arrivee, date_demandee"),
        @Index(name = "idx_statut_route", columnList = "statut, ville_depart, ville_arrivee")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeCollective {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "organisateur_id", nullable = false)
    private String organisateurId;

    @Column(name = "ville_depart", nullable = false, length = 100)
    private String departureCity;

    @Column(name = "ville_arrivee", nullable = false, length = 100)
    private String arrivalCity;

    @Column(name = "date_demandee", nullable = false)
    private LocalDate requestedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_vehicule", nullable = false)
    private VehicleType vehicleType;

    @Column(name = "capacite_totale", nullable = false)
    private int totalCapacity;

    @Column(name = "seuil_personnalise")
    private Integer seuilPersonnalise;

    @Column(name = "total_places_reservees", nullable = false)
    @Builder.Default
    private int totalSeatsReserved = 0;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, name = "statut")
    private DemandeStatus status = DemandeStatus.OPEN;

    @Column(name = "date_declenchement")
    private LocalDateTime triggeredAt;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        this.totalCapacity = vehicleType.getCapacity();
        if (this.seuilPersonnalise != null && this.seuilPersonnalise > this.totalCapacity) {
            throw new IllegalArgumentException("Le seuil ne peut pas dépasser la capacité totale");
        }
    }
}