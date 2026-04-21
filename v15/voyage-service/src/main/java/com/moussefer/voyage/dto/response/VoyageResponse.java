package com.moussefer.voyage.dto.response;

import com.moussefer.voyage.entity.Voyage;
import com.moussefer.voyage.entity.VoyageStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class VoyageResponse {
    private String id;
    private String organizerId;
    private String departureCity;
    private String arrivalCity;
    private LocalDateTime departureDate;
    private int totalSeats;
    private int availableSeats;
    private Double pricePerSeat;
    private VoyageStatus status;
    private LocalDateTime createdAt;

    public static VoyageResponse from(Voyage voyage) {
        return VoyageResponse.builder()
                .id(voyage.getId())
                .organizerId(voyage.getOrganizerId())
                .departureCity(voyage.getDepartureCity())
                .arrivalCity(voyage.getArrivalCity())
                .departureDate(voyage.getDepartureDate())
                .totalSeats(voyage.getTotalSeats())
                .availableSeats(voyage.getAvailableSeats())
                .pricePerSeat(voyage.getPricePerSeat())
                .status(voyage.getStatus())
                .createdAt(voyage.getCreatedAt())
                .build();
    }
}