package com.moussefer.loyalty.dto.response;

import com.moussefer.loyalty.entity.LoyaltyAccount;
import com.moussefer.loyalty.entity.LoyaltyTier;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LoyaltyAccountResponse {
    private String id;
    private String userId;
    private int points;
    private int totalEarned;
    private int totalRedeemed;
    private LoyaltyTier tier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LoyaltyAccountResponse from(LoyaltyAccount a) {
        return LoyaltyAccountResponse.builder()
                .id(a.getId())
                .userId(a.getUserId())
                .points(a.getPoints())
                .totalEarned(a.getTotalEarned())
                .totalRedeemed(a.getTotalRedeemed())
                .tier(a.getTier())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
