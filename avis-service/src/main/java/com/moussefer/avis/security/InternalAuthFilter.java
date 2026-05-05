package com.moussefer.avis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    @Value("${internal.api-key}")
    private String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only filter internal endpoints — public/self-service endpoints pass through
        // Internal endpoints are called by other microservices (admin-service, payment-service, etc.)
        boolean isInternalPath = path.contains("/internal/");
        return !isInternalPath;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("X-Internal-Secret");
        if (header == null || !internalSecret.equals(header)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"FORBIDDEN\",\"message\":\"Missing or invalid internal secret\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}