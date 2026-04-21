package com.moussefer.avis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverRatingUpdatedEvent {
    private String driverId;
    private double averageRating;
    private long totalReviews;
    private int lastRating;
}