package com.moussefer.admin.service;

import com.moussefer.admin.dto.request.AdminRoleRequest;
import com.moussefer.admin.entity.AdminRole;
import com.moussefer.admin.repository.AdminRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ADMIN-01: CRUD for dynamic admin roles stored in the admin_roles table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRoleService {

    private final AdminRoleRepository adminRoleRepository;

    public List<AdminRole> getAll() {
        return adminRoleRepository.findAll();
    }

    public AdminRole getByName(String name) {
        return adminRoleRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + name));
    }

    public AdminRole getById(String id) {
        return adminRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));
    }

    @Transactional
    public AdminRole create(AdminRoleRequest request) {
        String name = request.getName().toUpperCase();
        if (adminRoleRepository.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("Role already exists: " + name);
        }
        AdminRole role = AdminRole.builder()
                .name(name)
                .label(request.getLabel())
                .description(request.getDescription())
                .permissions(request.getPermissions())
                .modules(request.getModules())
                .build();
        log.info("ADMIN-01: creating new admin role '{}'", name);
        return adminRoleRepository.save(role);
    }

    @Transactional
    public AdminRole update(String id, AdminRoleRequest request) {
        AdminRole role = getById(id);
        role.setLabel(request.getLabel());
        role.setDescription(request.getDescription());
        role.setPermissions(request.getPermissions());
        role.setModules(request.getModules());
        log.info("ADMIN-01: updated admin role '{}'", role.getName());
        return adminRoleRepository.save(role);
    }

    @Transactional
    public void delete(String id) {
        AdminRole role = getById(id);
        // Protect the 6 original seeded roles from deletion
        List<String> protectedRoles = List.of(
                "SUPER_ADMIN", "OPERATIONAL_ADMIN", "FINANCIAL_ADMIN",
                "MODERATOR", "REPORTER", "AUDITEUR"
        );
        if (protectedRoles.contains(role.getName())) {
            throw new IllegalArgumentException(
                    "Cannot delete seeded role '" + role.getName() + "'. Deactivate it instead.");
        }
        adminRoleRepository.deleteById(id);
        log.info("ADMIN-01: deleted admin role '{}'", role.getName());
    }

    @Transactional
    public AdminRole setActive(String id, boolean active) {
        AdminRole role = getById(id);
        role.setActive(active);
        log.info("ADMIN-01: role '{}' active={}", role.getName(), active);
        return adminRoleRepository.save(role);
    }
}
