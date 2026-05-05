package com.moussefer.gateway.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.util.Map;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/auth")
    public Mono<Map<String, String>> authFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Auth service unavailable"));
    }

    @RequestMapping("/fallback/user")
    public Mono<Map<String, String>> userFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "User service unavailable"));
    }

    @RequestMapping("/fallback/trajet")
    public Mono<Map<String, String>> trajetFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Trajet service unavailable"));
    }

    @RequestMapping("/fallback/reservation")
    public Mono<Map<String, String>> reservationFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Reservation service unavailable"));
    }

    @RequestMapping("/fallback/payment")
    public Mono<Map<String, String>> paymentFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Payment service unavailable"));
    }

    @RequestMapping("/fallback/notification")
    public Mono<Map<String, String>> notificationFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Notification service unavailable"));
    }

    @RequestMapping("/fallback/chat")
    public Mono<Map<String, String>> chatFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Chat service unavailable"));
    }

    @RequestMapping("/fallback/voyage")
    public Mono<Map<String, String>> voyageFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Voyage service unavailable"));
    }

    @RequestMapping("/fallback/demande")
    public Mono<Map<String, String>> demandeFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Demande service unavailable"));
    }

    @RequestMapping("/fallback/avis")
    public Mono<Map<String, String>> avisFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Avis service unavailable"));
    }

    @RequestMapping("/fallback/station")
    public Mono<Map<String, String>> stationFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Station service unavailable"));
    }

    @RequestMapping("/fallback/analytics")
    public Mono<Map<String, String>> analyticsFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Analytics service unavailable"));
    }

    @RequestMapping("/fallback/admin")
    public Mono<Map<String, String>> adminFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Admin service unavailable"));
    }

    @RequestMapping("/fallback/loyalty")
    public Mono<Map<String, String>> loyaltyFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Loyalty service unavailable"));
    }

    @RequestMapping("/fallback/banner")
    public Mono<Map<String, String>> bannerFallback() {
        return Mono.just(Map.of("error", "SERVICE_UNAVAILABLE", "message", "Banner service unavailable"));
    }
}