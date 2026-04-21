package com.moussefer.banner.dto.request;

import com.moussefer.banner.entity.BannerAudience;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BannerRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Image URL is required")
    private String imageUrl;

    private String redirectUrl;
    private int displayOrder;
    private BannerAudience targetAudience;
    private boolean active;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
}
