package com.moussefer.admin.dto.request;

import lombok.Data;

@Data
public class VerifyUserRequest {
    private String status; // VERIFIED or REJECTED
    private String rejectionReason;
}
