package com.moussefer.user.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.Duration;

@Data
public class SuspendUserRequest {
    @NotNull
    private Duration duration;
    private String reason;
}