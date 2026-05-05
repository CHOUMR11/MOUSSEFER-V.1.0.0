package com.moussefer.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();

            // set() remplace toute valeur existante — évite les doublons
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            // HSTS 2 ans avec preload — standard industrie 2024
            headers.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            // X-XSS-Protection: 0 recommandé par OWASP (laisser CSP gérer)
            headers.set("X-XSS-Protection", "0");
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.set("Permissions-Policy", "geolocation=(), camera=(), microphone=(), payment=()");
            // CSP inclut wss: pour le chat WebSocket + data: pour les QR codes
            headers.set("Content-Security-Policy",
                    "default-src 'self'; " +
                            "script-src 'self'; " +
                            "style-src 'self' 'unsafe-inline'; " +
                            "img-src 'self' data:; " +
                            "connect-src 'self' ws: wss:; " +
                            "frame-ancestors 'none';"
            );
            // Supprime les bannières de technologie
            headers.remove("X-Powered-By");
            headers.remove("Server");
        }));
    }

    @Override
    public int getOrder() {
        return -3;
    }
}