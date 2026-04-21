package com.moussefer.avis.dto;

import com.moussefer.avis.entity.Avis;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AvisResponse {
    private String id;
    private String driverId;
    private String reservationId;
    private int rating;
    private String comment;
    private LocalDateTime createdAt;

    public static AvisResponse from(Avis avis) {
        return AvisResponse.builder()
                .id(avis.getId())
                .driverId(avis.getDriverId())
                .reservationId(avis.getReservationId())
                .rating(avis.getRating())
                .comment(avis.getComment())
                .createdAt(avis.getCreatedAt())
                .build();
    }
}