package com.moussefer.user.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 80) private String firstName;
    @Size(max = 80) private String lastName;
    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$") private String phoneNumber;
    @Size(max = 512) private String profilePictureUrl;

    // Driver fields
    @Size(max = 20) private String cinNumber;
    @Size(max = 50) private String driverLicenseNumber;
    @Size(max = 80) private String vehicleBrand;
    @Size(max = 80) private String vehicleModel;
    @Size(max = 20) private String vehiclePlate;
    @Size(max = 40) private String vehicleColor;
    private Integer vehicleYear;

    // Organizer fields
    @Size(max = 150) private String companyName;
    @Size(max = 80) private String companyRegistration;
}