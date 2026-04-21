package com.moussefer.station.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "secondary_points")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecondaryPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "station_id", nullable = false)
    private String stationId;

    @Column(nullable = false)
    private String name;

    private String address;

    private Double latitude;

    private Double longitude;

    @Column(name = "display_order")
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
