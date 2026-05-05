package com.moussefer.banner.dto.response;

import com.moussefer.banner.entity.Banner;
import com.moussefer.banner.entity.BannerAudience;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class BannerResponse {
    private String id;
    private String title;
    private String imageUrl;
    private String redirectUrl;
    private int displayOrder;
    private BannerAudience targetAudience;
    private boolean active;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime createdAt;

    public static BannerResponse from(Banner b) {
        return BannerResponse.builder()
                .id(b.getId())
                .title(b.getTitle())
                .imageUrl(b.getImageUrl())
                .redirectUrl(b.getRedirectUrl())
                .displayOrder(b.getDisplayOrder())
                .targetAudience(b.getTargetAudience())
                .active(b.isActive())
                .startsAt(b.getStartsAt())
                .endsAt(b.getEndsAt())
                .createdAt(b.getCreatedAt())
                .build();
    }
}
