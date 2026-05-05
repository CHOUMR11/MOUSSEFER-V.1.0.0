package com.moussefer.admin.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ADMIN-01: Persistent admin roles, replacing the hardcoded Set in AdminRoleGuard.
 * Seeded via Flyway migration V2__seed_admin_roles.sql.
 */
@Entity
@Table(name = "admin_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminRole {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name; // e.g. SUPER_ADMIN

    @Column(name = "label", length = 100)
    private String label; // e.g. "Super Administrateur"

    @Column(name = "description", length = 300)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission")
    private List<String> permissions;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "admin_role_modules", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "module_code")
    private List<String> modules;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
