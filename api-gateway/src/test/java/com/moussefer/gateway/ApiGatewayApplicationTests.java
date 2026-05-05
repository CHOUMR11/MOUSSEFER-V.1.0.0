package com.moussefer.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApiGatewayApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void contextLoads() {
    }

    @Test
    void actuatorHealthShouldBeAccessible() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void protectedRouteShouldReturn401AndCorrelationId() {
        webTestClient.get().uri("/test/anything")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Correlation-Id");
    }

    @Test
    void fallbackShouldReturnServiceUnavailable() {
        webTestClient.get().uri("/fallback/payment")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.error").isEqualTo("SERVICE_UNAVAILABLE");
    }
}