package com.moussefer.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke integration test verifying that the Spring ApplicationContext loads
 * correctly with all beans wired (controllers, services, repositories,
 * WebClients, Kafka, scheduler).
 *
 * This is a minimal smoke test — it catches major misconfiguration (missing
 * beans, circular dependencies, invalid YAML, broken autowiring) without
 * requiring external infrastructure (DB, Kafka, Redis).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.listener.auto-startup=false",
        "eureka.client.enabled=false",
        "internal.api-key=test-secret",
        "trajet.service.url=http://localhost:8083",
        "user.service.url=http://localhost:8082",
        "payment.service.url=http://localhost:8085",
        "spring.redis.host=localhost",
        "spring.redis.port=6379"
})
class ReservationServiceApplicationIT {

    @Test
    void contextLoads() {
        // Verifies that the entire Spring context boots successfully.
        // If any bean is missing, mis-wired, or has invalid configuration,
        // this test will fail immediately.
    }
}
