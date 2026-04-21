package com.moussefer.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "user_profiles", indexes = {
        @Index(name = "idx_user_id", columnList = "user_id", unique = true),
        @Index(name = "idx_role", columnList = "role")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false)
    private String email;

    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "profile_picture_url", length = 512)
    private String profilePictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    // Driver fields
    @Column(name = "cin_number", length = 20)
    private String cinNumber;

    @Column(name = "driver_license_number", length = 50)
    private String driverLicenseNumber;

    @Column(name = "vehicle_brand", length = 80)
    private String vehicleBrand;

    @Column(name = "vehicle_model", length = 80)
    private String vehicleModel;

    @Column(name = "vehicle_plate", length = 20)
    private String vehiclePlate;

    @Column(name = "vehicle_color", length = 40)
    private String vehicleColor;

    @Column(name = "vehicle_year")
    private Integer vehicleYear;

    @Column(name = "average_rating")
    @Builder.Default
    private Double averageRating = 0.0;

    @Column(name = "total_trips")
    @Builder.Default
    private Integer totalTrips = 0;

    // Organizer fields
    @Column(name = "company_name", length = 150)
    private String companyName;

    @Column(name = "company_registration", length = 80)
    private String companyRegistration;

    @Column(name = "is_verified_organizer")
    @Builder.Default
    private boolean verifiedOrganizer = false;

    // KYC verification
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status")
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Suspension (set by admin-service via internal call)
    @Column(name = "suspended_until")
    private LocalDateTime suspendedUntil;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    // FCM token
    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @ElementCollection
    @CollectionTable(name = "user_documents", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "document_type")
    @Column(name = "document_url", length = 1024)
    @Builder.Default
    private Map<String, String> documentUrls = new HashMap<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void addDocumentUrl(String documentType, String url) {
        if (documentUrls == null) documentUrls = new HashMap<>();
        documentUrls.put(documentType, url);
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}