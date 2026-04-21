package com.moussefer.loyalty.dto.response;

import com.moussefer.loyalty.entity.PointTransaction;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PointTransactionResponse {
    private String id;
    private int pointsDelta;
    private String reason;
    private String referenceId;
    private LocalDateTime createdAt;

    public static PointTransactionResponse from(PointTransaction t) {
        return PointTransactionResponse.builder()
                .id(t.getId())
                .pointsDelta(t.getPointsDelta())
                .reason(t.getReason())
                .referenceId(t.getReferenceId())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
