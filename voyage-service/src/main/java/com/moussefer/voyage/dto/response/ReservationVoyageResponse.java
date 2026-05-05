package com.moussefer.voyage.dto.response;

import com.moussefer.voyage.entity.ReservationVoyage;
import com.moussefer.voyage.entity.ReservationVoyageStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ReservationVoyageResponse {
    private String id;
    private String voyageId;
    private String passengerId;
    private int seatsReserved;
    private Double totalPrice;
    private String invoiceUrl;
    private ReservationVoyageStatus status;
    private LocalDateTime confirmedAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static ReservationVoyageResponse from(ReservationVoyage r) {
        return ReservationVoyageResponse.builder()
                .id(r.getId())
                .voyageId(r.getVoyageId())
                .passengerId(r.getPassengerId())
                .seatsReserved(r.getSeatsReserved())
                .totalPrice(r.getTotalPrice())
                .invoiceUrl(r.getInvoiceUrl())
                .status(r.getStatus())
                .confirmedAt(r.getConfirmedAt())
                .paidAt(r.getPaidAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}