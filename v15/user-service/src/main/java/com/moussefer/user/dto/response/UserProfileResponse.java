package com.moussefer.user.dto.response;

import com.moussefer.user.entity.UserProfile;
import com.moussefer.user.entity.UserRole;
import com.moussefer.user.entity.VerificationStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileResponse {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String profilePictureUrl;
    private UserRole role;
    private boolean active;
    private Double averageRating;
    private Integer totalTrips;
    private String cinNumber;
    private String driverLicenseNumber;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehiclePlate;
    private String vehicleColor;
    private Integer vehicleYear;
    private String companyName;
    private String companyRegistration;
    private boolean verifiedOrganizer;
    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private String createdAt;
    private String updatedAt;

    public static UserProfileResponse from(UserProfile profile) {
        if (profile == null) return null;
        return UserProfileResponse.builder()
                .userId(profile.getUserId())
                .email(profile.getEmail())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .profilePictureUrl(profile.getProfilePictureUrl())
                .role(profile.getRole())
                .active(profile.isActive())
                .averageRating(profile.getAverageRating())
                .totalTrips(profile.getTotalTrips())
                .cinNumber(profile.getCinNumber())
                .driverLicenseNumber(profile.getDriverLicenseNumber())
                .vehicleBrand(profile.getVehicleBrand())
                .vehicleModel(profile.getVehicleModel())
                .vehiclePlate(profile.getVehiclePlate())
                .vehicleColor(profile.getVehicleColor())
                .vehicleYear(profile.getVehicleYear())
                .companyName(profile.getCompanyName())
                .companyRegistration(profile.getCompanyRegistration())
                .verifiedOrganizer(profile.isVerifiedOrganizer())
                .verificationStatus(profile.getVerificationStatus())
                .rejectionReason(profile.getRejectionReason())
                .createdAt(profile.getCreatedAt() != null ? profile.getCreatedAt().toString() : null)
                .updatedAt(profile.getUpdatedAt() != null ? profile.getUpdatedAt().toString() : null)
                .build();
    }
}