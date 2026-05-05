package com.moussefer.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserActionRequest {
    @NotBlank
    private String targetUserId;

    @NotBlank
    private String action; // SUSPEND, ACTIVATE, DELETE

    private String reason;
}
