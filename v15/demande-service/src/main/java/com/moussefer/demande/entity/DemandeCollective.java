package com.moussefer.demande.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "demandes_collectives")
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

    @Column(name = "ville_depart", nullable = false)
    private String departureCity;

    @Column(name = "ville_arrivee", nullable = false)
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
    private int totalSeatsReserved;

    @Enumerated(EnumType.STRING)
    private DemandeStatus status;

    @Column(name = "date_declenchement")
    private LocalDateTime triggeredAt;

    @Column(name = "date_creation", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}