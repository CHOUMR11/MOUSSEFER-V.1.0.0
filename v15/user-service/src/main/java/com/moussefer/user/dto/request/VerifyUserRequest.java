package com.moussefer.user.dto.request;

import com.moussefer.user.entity.VerificationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VerifyUserRequest {
    @NotNull
    private VerificationStatus status;
    private String rejectionReason;
}