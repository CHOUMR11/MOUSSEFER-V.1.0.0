package com.moussefer.gateway.filter;

import com.moussefer.gateway.config.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtService jwtService;
    @Value("${internal.api-key}")
    private String internalSecret;

    private static final List<String> PUBLIC_ROUTES = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",                 // V23 — accepts expired access tokens
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password",
            "/api/v1/auth/oauth2",
            "/api/v1/payments/webhook",
            "/api/v1/voyages/webhook",
            "/api/v1/banners",                   // ← public read access
            "/api/v1/stations",                  // ← public station list
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui",
            "/v3/api-docs",
            "/webjars",
            "/api/v1/auth/v3/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1. Routes publiques
        if (isPublicRoute(path)) {
            return chain.filter(exchange);
        }

        // 2. Routes internes (/internal/**) : deux modes d'accès
        //    a. Inter-services : header X-Internal-Secret valide (cas legacy)
        //    b. Frontend admin : JWT valide avec role=ADMIN sur /internal/admin/**
        //       Le gateway authentifie via JWT puis INJECTE le secret côté serveur.
        //       Le client ne voit jamais le secret.
        if (path.contains("/internal/")) {
            String internalSecretHeader = exchange.getRequest().getHeaders().getFirst("X-Internal-Secret");

            // Mode (a) : appel inter-services avec le bon secret → laissez passer
            if (Objects.equals(internalSecret, internalSecretHeader)) {
                return chain.filter(exchange);
            }

            // Mode (b) : seulement pour /internal/admin/** et avec JWT ADMIN valide
            if (path.contains("/internal/admin/")) {
                String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    String token = authHeader.substring(7);
                    if (jwtService.isTokenValid(token)) {
                        try {
                            String role = jwtService.extractRole(token);
                            if ("ADMIN".equalsIgnoreCase(role)) {
                                // Authentifié comme admin → on enrichit la requête
                                // avec le secret + les claims, le client n'a jamais
                                // touché au secret.
                                Claims claims = jwtService.extractAllClaims(token);
                                String userId = jwtService.extractUserId(token);
                                String email = claims.getSubject();
                                String adminRole = jwtService.extractAdminRole(token);

                                ServerHttpRequest.Builder rb = exchange.getRequest().mutate()
                                        .headers(h -> {
                                            h.remove("X-User-Id");
                                            h.remove("X-User-Email");
                                            h.remove("X-User-Role");
                                            h.remove("X-Admin-Role");
                                            h.remove("X-Internal-Secret");
                                        })
                                        .header("X-User-Id", userId)
                                        .header("X-User-Email", email)
                                        .header("X-User-Role", role)
                                        .header("X-Internal-Secret", internalSecret);
                                if (adminRole != null && !adminRole.isBlank()) {
                                    rb.header("X-Admin-Role", adminRole);
                                }
                                log.info("Admin {} accessing /internal/admin/** path={}", userId, path);
                                return chain.filter(exchange.mutate().request(rb.build()).build());
                            }
                        } catch (Exception e) {
                            log.warn("JWT processing failed on /internal/admin/ path: {}", e.getMessage());
                        }
                    }
                }
            }

            // Aucun mode valide : refus
            log.warn("Internal route access denied: missing/invalid secret AND no admin JWT for path {}", path);
            return unauthorized(exchange.getResponse(), "Internal endpoint requires valid secret or admin JWT");
        }

        // 3. Routes protégées : validation JWT
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange.getResponse(), "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        if (!jwtService.isTokenValid(token)) {
            return unauthorized(exchange.getResponse(), "Invalid or expired JWT token");
        }

        try {
            Claims claims = jwtService.extractAllClaims(token);
            String userId = jwtService.extractUserId(token);
            String role = jwtService.extractRole(token);
            String email = claims.getSubject();
            String adminRole = jwtService.extractAdminRole(token);

            // ⚠️ CORRECTION CRITIQUE : Supprimer les en-têtes forgés par le client
            // avant d'ajouter les valeurs authentifiées
            ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                    .headers(httpHeaders -> {
                        httpHeaders.remove("X-User-Id");
                        httpHeaders.remove("X-User-Email");
                        httpHeaders.remove("X-User-Role");
                        httpHeaders.remove("X-Admin-Role");
                        httpHeaders.remove("X-Internal-Secret");
                    })
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email)
                    .header("X-User-Role", role)
                    .header("X-Internal-Secret", internalSecret);

            if (adminRole != null && !adminRole.isBlank()) {
                requestBuilder.header("X-Admin-Role", adminRole);
            }

            String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
            if (correlationId != null) {
                requestBuilder.header("X-Correlation-Id", correlationId);
            }

            ServerHttpRequest mutatedRequest = requestBuilder.build();
            log.debug("Authenticated request: userId={}, role={}, adminRole={}, path={}", userId, role, adminRole, path);
            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.error("JWT processing error: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "Token processing failed");
        }
    }

    private boolean isPublicRoute(String path) {
        return PUBLIC_ROUTES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorized(ServerHttpResponse response, String reason) {
        log.warn("Unauthorized: {}", reason);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        var body = response.bufferFactory().wrap(
                ("{\"error\":\"UNAUTHORIZED\",\"message\":\"" + reason + "\"}").getBytes()
        );
        return response.writeWith(Mono.just(body));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}