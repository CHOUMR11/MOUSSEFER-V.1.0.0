package com.moussefer.user.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriverInfoResponse {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String vehicleBrand;
    private String vehicleModel;
    private String vehiclePlate;
    private String vehicleColor;
}