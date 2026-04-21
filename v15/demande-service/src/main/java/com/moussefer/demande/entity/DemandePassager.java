package com.moussefer.demande.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "demande_passagers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandePassager {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "demande_id", nullable = false)
    private String demandeId;

    @Column(name = "passenger_id", nullable = false)
    private String passengerId;

    @Column(name = "seats_reserved", nullable = false)
    private int seatsReserved;

    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        joinedAt = LocalDateTime.now();
    }
}