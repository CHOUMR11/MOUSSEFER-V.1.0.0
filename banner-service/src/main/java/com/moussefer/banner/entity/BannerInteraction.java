package com.moussefer.banner.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "banner_interactions", indexes = {
        @Index(name = "idx_interaction_banner", columnList = "banner_id"),
        @Index(name = "idx_interaction_type", columnList = "interaction_type")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BannerInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "banner_id", nullable = false)
    private String bannerId;

    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false)
    private InteractionType interactionType;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum InteractionType {
        IMPRESSION, CLICK
    }
}
