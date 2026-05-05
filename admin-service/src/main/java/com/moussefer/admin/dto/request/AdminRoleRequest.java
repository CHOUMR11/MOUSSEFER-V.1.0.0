package com.moussefer.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/** ADMIN-01: Request body for creating or updating a dynamic admin role. */
@Data
public class AdminRoleRequest {

    @NotBlank(message = "Role name is required")
    @Pattern(regexp = "^[A-Z_]{3,50}$", message = "Role name must be uppercase letters and underscores, 3-50 chars")
    private String name;

    @NotBlank(message = "Label is required")
    private String label;

    private String description;

    private List<String> permissions;

    private List<String> modules;
}
