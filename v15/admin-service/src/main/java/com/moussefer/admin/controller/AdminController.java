package com.moussefer.admin.controller;

import com.moussefer.admin.dto.request.SuspendUserRequest;
import com.moussefer.admin.dto.request.UserActionRequest;
import com.moussefer.admin.dto.request.VerifyUserRequest;
import com.moussefer.admin.dto.response.AuditLogResponse;
import com.moussefer.admin.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Backoffice", description = "All admin operations — user management, audit, dashboard, role simulation")
public class AdminController {

    private final AdminService adminService;
    private final WebClient userServiceWebClient;

    // ══════════════════════════════════════════════════════════════
    // M01 — ROLE MANAGEMENT & SIMULATION
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/users/{userId}/admin-role")
    @Operation(summary = "Assign admin role (SUPER_ADMIN only)")
    public ResponseEntity<Map> assignAdminRole(
            @PathVariable String userId, @RequestHeader("X-User-Id") String adminId,
            @RequestBody Map<String, String> request, HttpServletRequest httpReq) {
        Map result = delegatePost("/api/v1/users/internal/admin/" + userId + "/admin-role", request, adminId);
        adminService.logAction(adminId, "ASSIGN_ADMIN_ROLE", "USER", userId, "Role: " + request.get("adminRole"), httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/{userId}/admin-role")
    @Operation(summary = "Get admin role of a user")
    public ResponseEntity<Map> getAdminRole(@PathVariable String userId) {
        return ResponseEntity.ok(delegateGet("/api/v1/users/internal/admin/" + userId + "/admin-role"));
    }

    @GetMapping("/simulate-role/{targetRole}")
    @Operation(summary = "SUPER_ADMIN: simulate backoffice permissions for a role (M01)")
    public ResponseEntity<Map<String, Object>> simulateRole(@PathVariable String targetRole, @RequestHeader("X-User-Id") String adminId) {
        Map<String, List<String>> perms = Map.of(
            "SUPER_ADMIN", List.of("M01","M02","M03","M04","M05","M06","M07","M08","M09","CREATE_ADMIN","FORCE_CANCEL","FORCE_CONFIRM","REFUND","VIEW_AUDIT","SIMULATE_ROLE"),
            "OPERATIONAL_ADMIN", List.of("M02","M03","M04","M05","FORCE_CANCEL","ASSIGN_DRIVER","CANCEL_TRAJET"),
            "FINANCIAL_ADMIN", List.of("M06","M08","M09","REFUND","VIEW_COMMISSIONS","VIEW_PAYOUTS","EXPORT_CSV"),
            "MODERATOR", List.of("M02","M05","M07","VERIFY_USER","SUSPEND_USER","RESOLVE_DISPUTE","MANAGE_BANNERS"),
            "REPORTER", List.of("M09","VIEW_STATS","VIEW_ANALYTICS","VIEW_AUDIT"),
            "AUDITEUR", List.of("M09","VIEW_AUDIT","VIEW_STATS")
        );
        String role = targetRole.toUpperCase();
        List<String> p = perms.getOrDefault(role, List.of());
        if (p.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Unknown role: " + targetRole));
        return ResponseEntity.ok(Map.of("simulatedRole", role, "simulatedBy", adminId,
            "accessibleModules", p.stream().filter(x -> x.startsWith("M0")).toList(),
            "permissions", p.stream().filter(x -> !x.startsWith("M0")).toList(),
            "note", "Simulation only — no actions performed"));
    }

    // ══════════════════════════════════════════════════════════════
    // M02 — USER MANAGEMENT (delegates to user-service)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/users")
    @Operation(summary = "List all users (paginated, filterable)")
    public ResponseEntity<Map> listUsers(
            @RequestParam(required = false) String role, @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        String uri = String.format("/api/v1/users/internal/admin/list?page=%d&size=%d", page, size);
        if (role != null) uri += "&role=" + role;
        if (status != null) uri += "&status=" + status;
        if (keyword != null) uri += "&keyword=" + keyword;
        return ResponseEntity.ok(delegateGet(uri));
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user profile by ID")
    public ResponseEntity<Map> getUser(@PathVariable String userId) {
        return ResponseEntity.ok(delegateGet("/api/v1/users/internal/admin/" + userId));
    }

    @PostMapping("/users/{userId}/deactivate")
    @Operation(summary = "Deactivate a user account")
    public ResponseEntity<Void> deactivateUser(@PathVariable String userId, @RequestHeader("X-User-Id") String adminId, HttpServletRequest httpReq) {
        delegatePostVoid("/api/v1/users/internal/admin/" + userId + "/deactivate", adminId);
        adminService.logAction(adminId, "DEACTIVATE_USER", "USER", userId, null, httpReq.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/reactivate")
    @Operation(summary = "Reactivate a user account")
    public ResponseEntity<Void> reactivateUser(@PathVariable String userId, @RequestHeader("X-User-Id") String adminId, HttpServletRequest httpReq) {
        delegatePostVoid("/api/v1/users/internal/admin/" + userId + "/reactivate", adminId);
        adminService.logAction(adminId, "REACTIVATE_USER", "USER", userId, null, httpReq.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/suspend")
    @Operation(summary = "Suspend a user temporarily")
    public ResponseEntity<Map> suspendUser(@PathVariable String userId, @RequestHeader("X-User-Id") String adminId,
            @Valid @RequestBody SuspendUserRequest request, HttpServletRequest httpReq) {
        Map result = delegatePost("/api/v1/users/internal/admin/" + userId + "/suspend", request, adminId);
        adminService.logAction(adminId, "SUSPEND_USER", "USER", userId, "Reason: " + request.getReason(), httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/{userId}/lift-suspension")
    @Operation(summary = "Lift user suspension")
    public ResponseEntity<Void> liftSuspension(@PathVariable String userId, @RequestHeader("X-User-Id") String adminId, HttpServletRequest httpReq) {
        delegatePostVoid("/api/v1/users/internal/admin/" + userId + "/lift-suspension", adminId);
        adminService.logAction(adminId, "LIFT_SUSPENSION", "USER", userId, null, httpReq.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/verify")
    @Operation(summary = "Verify or reject user documents")
    public ResponseEntity<Map> verifyUser(@PathVariable String userId, @RequestHeader("X-User-Id") String adminId,
            @Valid @RequestBody VerifyUserRequest request, HttpServletRequest httpReq) {
        Map result = delegatePost("/api/v1/users/internal/admin/" + userId + "/verify", request, adminId);
        adminService.logAction(adminId, "VERIFY_USER", "USER", userId, "Status: " + request.getStatus(), httpReq.getRemoteAddr());
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════════
    // M09 — DASHBOARD STATS
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/dashboard/users/stats")
    @Operation(summary = "User statistics for admin dashboard")
    public ResponseEntity<Map> userStats() {
        return ResponseEntity.ok(delegateGet("/api/v1/users/internal/admin/stats"));
    }

    // ══════════════════════════════════════════════════════════════
    // AUDIT LOGS (owned by admin-service DB)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/audit-logs")
    @Operation(summary = "List all audit logs (paginated)")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAuditLogs(pageable).map(AuditLogResponse::from));
    }

    @GetMapping("/activity-logs")
    @Operation(summary = "Alias of /audit-logs (frontend compatibility)")
    public ResponseEntity<Page<AuditLogResponse>> getActivityLogs(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAuditLogs(pageable).map(AuditLogResponse::from));
    }

    @GetMapping("/audit-logs/admin/{adminId}")
    @Operation(summary = "Get audit logs by admin ID")
    public ResponseEntity<Page<AuditLogResponse>> getByAdmin(@PathVariable String adminId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getLogsByAdmin(adminId, pageable).map(AuditLogResponse::from));
    }

    @GetMapping("/audit-logs/target/{type}/{id}")
    @Operation(summary = "Get audit logs for a specific entity")
    public ResponseEntity<Page<AuditLogResponse>> getByTarget(@PathVariable String type, @PathVariable String id, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getLogsByTarget(type, id, pageable).map(AuditLogResponse::from));
    }

    // ══════════════════════════════════════════════════════════════
    // LOYALTY (admin override)
    // ══════════════════════════════════════════════════════════════

    @GetMapping("/users/{userId}/loyalty-points")
    @Operation(summary = "Get loyalty points of any user")
    public ResponseEntity<Map> getLoyaltyPoints(@PathVariable String userId) {
        return ResponseEntity.ok(delegateGet("/api/v1/users/internal/admin/" + userId + "/loyalty-points"));
    }

    @PostMapping("/users/{userId}/loyalty-points/add")
    @Operation(summary = "Add loyalty points to user (admin override)")
    public ResponseEntity<Void> addLoyaltyPoints(@PathVariable String userId, @RequestHeader("X-User-Id") String adminId,
            @RequestBody Map<String, Object> body, HttpServletRequest httpReq) {
        userServiceWebClient.post().uri("/api/v1/users/internal/admin/" + userId + "/loyalty-points/add")
                .bodyValue(body).retrieve().bodyToMono(Void.class).block(Duration.ofSeconds(5));
        adminService.logAction(adminId, "ADD_LOYALTY_POINTS", "USER", userId, "Points: " + body.get("points"), httpReq.getRemoteAddr());
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════════
    // GENERIC ACTION (backward compat)
    // ══════════════════════════════════════════════════════════════

    @PostMapping("/users/action")
    @Operation(summary = "Log a generic admin action")
    public ResponseEntity<AuditLogResponse> performUserAction(@RequestHeader("X-User-Id") String adminId,
            @Valid @RequestBody UserActionRequest request, HttpServletRequest httpReq) {
        var auditLog = adminService.logAction(adminId, request.getAction(), "USER", request.getTargetUserId(), request.getReason(), httpReq.getRemoteAddr());
        return ResponseEntity.ok(AuditLogResponse.from(auditLog));
    }

    // ══════════════════════════════════════════════════════════════
    // Private WebClient helpers
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("rawtypes")
    private Map delegateGet(String uri) {
        return userServiceWebClient.get().uri(uri).retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(5));
    }

    @SuppressWarnings("rawtypes")
    private Map delegatePost(String uri, Object body, String adminId) {
        return userServiceWebClient.post().uri(uri).header("X-Admin-Id", adminId)
                .bodyValue(body).retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(5));
    }

    private void delegatePostVoid(String uri, String adminId) {
        userServiceWebClient.post().uri(uri).header("X-Admin-Id", adminId)
                .retrieve().bodyToMono(Void.class).block(Duration.ofSeconds(5));
    }
}
