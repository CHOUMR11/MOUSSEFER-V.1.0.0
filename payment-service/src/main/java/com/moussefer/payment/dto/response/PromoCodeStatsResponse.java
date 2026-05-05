package com.moussefer.payment.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PromoCodeStatsResponse {
    private long totalCreated;
    private long totalActive;
    private long totalExpired;
    private long totalExhausted;  // maxUses reached
    private long totalUsages;
}
