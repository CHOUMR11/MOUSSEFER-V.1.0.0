package com.moussefer.admin.security;

import com.moussefer.admin.repository.AdminRoleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminRoleGuard extends OncePerRequestFilter {

    private final AdminRoleRepository adminRoleRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String adminRole = request.getHeader("X-Admin-Role");
        String path = request.getRequestURI();

        Set<String> validRoles = adminRoleRepository.findAllActiveRoleNames();

        if (adminRole == null || adminRole.isBlank()
                || !validRoles.contains(adminRole.toUpperCase())) {
            log.warn("Admin access denied: path={}, adminRole={}", path, adminRole);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Admin role required\"}");
            return;
        }

        String role = adminRole.toUpperCase();

        if (path.contains("/audit-logs")
                && !role.equals("SUPER_ADMIN") && !role.equals("AUDITEUR")) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Audit logs require SUPER_ADMIN or AUDITEUR role\"}");
            return;
        }

        // FIX #9: protect role mutations
        boolean isRoleMutation = (path.contains("/admin-role") ||
                path.matches(".*/api/v1/admin/roles(/.*)?$")) &&
                !request.getMethod().equalsIgnoreCase("GET");
        if (isRoleMutation && !role.equals("SUPER_ADMIN")) {
            log.warn("Role mutation denied: role={}, method={}, path={}", role, request.getMethod(), path);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN can create or modify admin roles\"}");
            return;
        }

        if (path.contains("/simulate-role") && !role.equals("SUPER_ADMIN")) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN can simulate roles\"}");
            return;
        }

        // V17: user creation
        boolean isUserCreation = path.matches(".*/api/v1/admin/users/?$")
                && request.getMethod().equalsIgnoreCase("POST");
        if (isUserCreation && !role.equals("SUPER_ADMIN")) {
            log.warn("User creation denied: role={}, path={}", role, path);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN can create new user accounts (including ORGANIZER)\"}");
            return;
        }

        // V19: fare management
        boolean isFareManagement = path.contains("/api/v1/admin/fares")
                && !request.getMethod().equalsIgnoreCase("GET");
        if (isFareManagement && !role.equals("SUPER_ADMIN") && !role.equals("FINANCIAL_ADMIN")) {
            log.warn("Fare management denied: role={}, method={}, path={}",
                    role, request.getMethod(), path);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN or FINANCIAL_ADMIN can manage regulated fares\"}");
            return;
        }

        // V21: feature toggles
        boolean isFeatureToggleMutation = path.contains("/api/v1/admin/features")
                && !request.getMethod().equalsIgnoreCase("GET");
        if (isFeatureToggleMutation && !role.equals("SUPER_ADMIN")) {
            log.warn("Feature toggle change denied: role={}, method={}, path={}",
                    role, request.getMethod(), path);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN can manage feature toggles\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(body);
    }
}