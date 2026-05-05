package com.moussefer.admin.dto.response;

import com.moussefer.admin.entity.AdminRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AdminRoleResponse {
    private String id;
    private String name;
    private String label;
    private String description;
    private List<String> permissions;
    private List<String> modules;
    private boolean active;
    private LocalDateTime createdAt;

    public static AdminRoleResponse from(AdminRole r) {
        return AdminRoleResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .label(r.getLabel())
                .description(r.getDescription())
                .permissions(r.getPermissions())
                .modules(r.getModules())
                .active(r.isActive())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
