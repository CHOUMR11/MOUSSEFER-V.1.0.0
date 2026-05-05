package com.moussefer.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicDriverProfileResponse {
    private String userId;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private Double averageRating;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehiclePlate;
}