package com.moussefer.admin.controller;

import com.moussefer.admin.dto.request.AdminRoleRequest;
import com.moussefer.admin.dto.response.AdminRoleResponse;
import com.moussefer.admin.service.AdminRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ADMIN-01 FIX: Full CRUD for admin roles (DB-backed, no longer hardcoded).
 *
 * All write operations are restricted to SUPER_ADMIN via AdminRoleGuard
 * (path contains "/roles" → only SUPER_ADMIN passes the /admin-role check).
 */
@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@Tag(name = "Admin – Roles", description = "Dynamic admin role management (ADMIN-01)")
public class AdminRolesController {

    private final AdminRoleService adminRoleService;

    @GetMapping
    @Operation(summary = "List all admin roles (DB-backed)")
    public ResponseEntity<List<AdminRoleResponse>> list() {
        return ResponseEntity.ok(
                adminRoleService.getAll().stream().map(AdminRoleResponse::from).toList()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a role by ID")
    public ResponseEntity<AdminRoleResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(AdminRoleResponse.from(adminRoleService.getById(id)));
    }

    @GetMapping("/name/{name}")
    @Operation(summary = "Get a role by name")
    public ResponseEntity<AdminRoleResponse> getByName(@PathVariable String name) {
        return ResponseEntity.ok(AdminRoleResponse.from(adminRoleService.getByName(name)));
    }

    @PostMapping
    @Operation(summary = "Create a custom admin role (SUPER_ADMIN only)")
    public ResponseEntity<AdminRoleResponse> create(@Valid @RequestBody AdminRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminRoleResponse.from(adminRoleService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a role's label, description, and permissions (SUPER_ADMIN only)")
    public ResponseEntity<AdminRoleResponse> update(
            @PathVariable String id,
            @Valid @RequestBody AdminRoleRequest request) {
        return ResponseEntity.ok(AdminRoleResponse.from(adminRoleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a custom role (SUPER_ADMIN only — seeded roles are protected)")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        adminRoleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a role without deleting it (SUPER_ADMIN only)")
    public ResponseEntity<AdminRoleResponse> deactivate(@PathVariable String id) {
        return ResponseEntity.ok(AdminRoleResponse.from(adminRoleService.setActive(id, false)));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Re-activate a previously deactivated role (SUPER_ADMIN only)")
    public ResponseEntity<AdminRoleResponse> activate(@PathVariable String id) {
        return ResponseEntity.ok(AdminRoleResponse.from(adminRoleService.setActive(id, true)));
    }
}
