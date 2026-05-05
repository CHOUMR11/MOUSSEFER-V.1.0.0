package com.moussefer.demande.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemandeThresholdReachedEvent {
    private String demandeId;
    private String organisateurId;
    private String departureCity;
    private String arrivalCity;
    private LocalDate requestedDate;
    private int totalSeatsReserved;
    private int thresholdUsed;
}