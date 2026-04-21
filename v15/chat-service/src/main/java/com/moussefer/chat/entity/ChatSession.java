package com.moussefer.chat.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_reference_id", columnList = "reference_id"),
        @Index(name = "idx_participant1", columnList = "participant1_id"),
        @Index(name = "idx_participant2", columnList = "participant2_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    private String referenceId;   // reservationId ou voyageReservationId

    @Column(name = "reference_id", nullable = false, unique = true)
    private String referenceIdCopy; // redondant pour les requêtes, sera égal à referenceId

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false)
    private SessionType sessionType;

    @Column(name = "participant1_id", nullable = false)  // passager
    private String participant1Id;

    @Column(name = "participant2_id", nullable = false)  // chauffeur ou organisateur
    private String participant2Id;

    @Column(name = "departure_time")
    private LocalDateTime departureTime;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        referenceIdCopy = referenceId;
    }
}