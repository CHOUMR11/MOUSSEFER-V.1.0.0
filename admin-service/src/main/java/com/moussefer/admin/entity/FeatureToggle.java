package com.moussefer.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Platform-wide feature toggle.
 *
 * Matches the admin "Fonctionnalités" page: a list of switches the
 * super-admin can flip to enable/disable platform capabilities
 * (e.g. disable reservations during maintenance, turn off organizer
 * bookings, enable/disable loyalty rewards, etc.).
 *
 * Reads are cached; writes invalidate cache. Non-admin users should
 * never write — the AdminRoleGuard enforces SUPER_ADMIN for mutations.
 */
@Entity
@Table(name = "feature_toggles",
       uniqueConstraints = @UniqueConstraint(name = "uk_feature_key", columnNames = "feature_key"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FeatureToggle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "feature_key", nullable = false, length = 100)
    private String featureKey;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /**
     * Category for grouping in the admin UI (e.g. "Réservations",
     * "Paiements", "Notifications", "Maintenance").
     */
    @Column(length = 50)
    private String category;

    @Column(name = "updated_by", length = 40)
    private String updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
