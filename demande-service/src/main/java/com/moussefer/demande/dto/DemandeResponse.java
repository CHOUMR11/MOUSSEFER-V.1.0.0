package com.moussefer.demande.dto;

import com.moussefer.demande.entity.DemandeCollective;
import com.moussefer.demande.entity.DemandeStatus;
import com.moussefer.demande.entity.VehicleType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class DemandeResponse {
    private String id;
    private String organisateurId;
    private String departureCity;
    private String arrivalCity;
    private LocalDate requestedDate;
    private VehicleType vehicleType;
    private int totalCapacity;
    private Integer seuilPersonnalise;
    private int totalSeatsReserved;
    private DemandeStatus status;
    private LocalDateTime triggeredAt;
    private LocalDateTime createdAt;

    public static DemandeResponse from(DemandeCollective demande) {
        return DemandeResponse.builder()
                .id(demande.getId())
                .organisateurId(demande.getOrganisateurId())
                .departureCity(demande.getDepartureCity())
                .arrivalCity(demande.getArrivalCity())
                .requestedDate(demande.getRequestedDate())
                .vehicleType(demande.getVehicleType())
                .totalCapacity(demande.getTotalCapacity())
                .seuilPersonnalise(demande.getSeuilPersonnalise())
                .totalSeatsReserved(demande.getTotalSeatsReserved())
                .status(demande.getStatus())
                .triggeredAt(demande.getTriggeredAt())
                .createdAt(demande.getCreatedAt())
                .build();
    }
}