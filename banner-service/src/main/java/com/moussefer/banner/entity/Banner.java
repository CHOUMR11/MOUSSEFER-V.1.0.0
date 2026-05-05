package com.moussefer.banner.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "banners")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "redirect_url")
    private String redirectUrl;

    @Column(name = "display_order")
    @Builder.Default
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience")
    @Builder.Default
    private BannerAudience targetAudience = BannerAudience.ALL;

    @Column(name = "target_city")
    private String targetCity;

    @Column(name = "target_region")
    private String targetRegion;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
