package com.moussefer.trajet.dto.response;

import com.moussefer.trajet.entity.Trajet;
import com.moussefer.trajet.entity.TrajetStatus;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TrajetResponse {
    private String id;
    private String driverId;
    private String departureCity;
    private String arrivalCity;
    private LocalDateTime departureDate;
    private int totalSeats;
    private int availableSeats;
    private TrajetStatus status;
    private int priorityOrder;
    private boolean reservable;
    private boolean acceptsPets;
    private boolean allowsLargeBags;
    private boolean airConditioned;
    private boolean hasIntermediateStops;
    private boolean directTrip;
    private BigDecimal pricePerSeat;
    private String notes;
    private LocalDateTime createdAt;

    public static TrajetResponse from(Trajet t, boolean reservable) {
        return TrajetResponse.builder()
                .id(t.getId())
                .driverId(t.getDriverId())
                .departureCity(t.getDepartureCity())
                .arrivalCity(t.getArrivalCity())
                .departureDate(t.getDepartureDate())
                .totalSeats(t.getTotalSeats())
                .availableSeats(t.getAvailableSeats())
                .status(t.getStatus())
                .priorityOrder(t.getPriorityOrder())
                .reservable(reservable)
                .acceptsPets(t.isAcceptsPets())
                .allowsLargeBags(t.isAllowsLargeBags())
                .airConditioned(t.isAirConditioned())
                .hasIntermediateStops(t.isHasIntermediateStops())
                .directTrip(t.isDirectTrip())
                .pricePerSeat(t.getPricePerSeat())
                .notes(t.getNotes())
                .createdAt(t.getCreatedAt())
                .build();
    }
}