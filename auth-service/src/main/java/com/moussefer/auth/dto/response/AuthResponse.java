package com.moussefer.auth.dto.response;

import com.moussefer.auth.entity.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String tokenType;
    private String userId;
    private String email;
    private Role role;
    private String adminRole; // null si non admin
}