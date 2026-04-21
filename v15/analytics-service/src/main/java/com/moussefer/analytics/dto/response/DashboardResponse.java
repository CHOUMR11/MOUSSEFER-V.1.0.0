package com.moussefer.analytics.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardResponse {
    private long totalBookings;
    private long totalCancellations;
    private long totalCompleted;
    private long totalEscalated;
    private long totalTrajetsPublished;
    private long totalNewUsers;
    private Double totalRevenue;
    private long bookingsToday;
    private long bookingsThisMonth;
    private long completedThisMonth;
    private Double revenueThisMonth;
}
