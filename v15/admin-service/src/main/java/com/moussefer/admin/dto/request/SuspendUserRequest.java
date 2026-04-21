package com.moussefer.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.Duration;

@Data
public class SuspendUserRequest {
    @NotBlank
    private String reason;
    private Duration duration = Duration.ofDays(7);
}
