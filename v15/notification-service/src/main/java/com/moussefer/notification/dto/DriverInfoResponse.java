package com.moussefer.notification.dto;

import lombok.Data;

@Data
public class DriverInfoResponse {
    private String userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
}