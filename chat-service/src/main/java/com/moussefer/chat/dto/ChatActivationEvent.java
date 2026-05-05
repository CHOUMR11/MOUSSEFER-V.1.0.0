package com.moussefer.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatActivationEvent {
    private String referenceId;      // reservationId ou voyageReservationId
    private String type;             // "RIDE" ou "ORGANIZED"
    private String passengerId;
    private String counterpartId;    // driverId ou organizerId
    private LocalDateTime departureTime;
}