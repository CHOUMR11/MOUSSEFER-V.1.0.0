package com.moussefer.user.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverRatingUpdatedEvent {
    private String driverId;
    private Double averageRating;
    private Integer totalReviews;
}