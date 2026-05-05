package com.moussefer.voyage.aspect;

import com.moussefer.voyage.annotation.RequiresAdminRole;
import com.moussefer.voyage.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminAuthorizationAspect {

    @Before("@annotation(requiresAdminRole)")
    public void checkAdminRole(JoinPoint joinPoint, RequiresAdminRole requiresAdminRole) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new BusinessException("Unable to retrieve request context");
        }
        HttpServletRequest request = attributes.getRequest();
        String adminRole = request.getHeader("X-Admin-Role");
        if (adminRole == null || adminRole.isBlank()) {
            log.warn("Admin role required for method {}", joinPoint.getSignature().getName());
            throw new BusinessException("Admin role required");
        }
        String[] allowedRoles = requiresAdminRole.value();
        boolean allowed = Arrays.stream(allowedRoles)
                .anyMatch(role -> role.equalsIgnoreCase(adminRole));
        if (!allowed && !"SUPER_ADMIN".equalsIgnoreCase(adminRole)) {
            log.warn("Insufficient permissions: user has role {}, required one of {}", adminRole, Arrays.toString(allowedRoles));
            throw new BusinessException("Insufficient permissions");
        }
    }
}