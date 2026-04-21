package com.moussefer.demande.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** FEAT-01: Response returned after merging similar collective demands. */
@Data
@Builder
public class MergeDemandeResponse {
    private String survivorDemandeId;
    private List<String> mergedDemandeIds;
    private int totalPassengersMoved;
    private int totalSeatsAfterMerge;
    private String message;
}
