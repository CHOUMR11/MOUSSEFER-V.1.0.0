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
        String method = request.getMethod();

        // Load valid roles dynamically from DB
        Set<String> validRoles = adminRoleRepository.findAllActiveRoleNames();

        if (adminRole == null || adminRole.isBlank()
                || !validRoles.contains(adminRole.toUpperCase())) {
            log.warn("Admin access denied: path={}, adminRole={}", path, adminRole);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Admin role required\"}");
            return;
        }

        String role = adminRole.toUpperCase();

        // ✅ Bug #10: Protect role mutations (POST/PUT/DELETE on /admin/roles)
        if (path.contains("/admin/roles") &&
                (method.equals("POST") || method.equals("PUT") || method.equals("DELETE"))) {
            if (!"SUPER_ADMIN".equals(role)) {
                log.warn("Role mutation denied: path={}, method={}, role={}", path, method, role);
                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                        "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN can modify admin roles\"}");
                return;
            }
        }

        // Existing path-specific restrictions
        if (path.contains("/audit-logs")
                && !role.equals("SUPER_ADMIN") && !role.equals("AUDITEUR")) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Audit logs require SUPER_ADMIN or AUDITEUR role\"}");
            return;
        }
        if (path.contains("/admin-role") && !role.equals("SUPER_ADMIN")) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN can assign admin roles\"}");
            return;
        }
        if (path.contains("/simulate-role") && !role.equals("SUPER_ADMIN")) {
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"error\":\"FORBIDDEN\",\"message\":\"Only SUPER_ADMIN can simulate roles\"}");
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