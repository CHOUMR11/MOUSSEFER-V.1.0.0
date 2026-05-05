package com.moussefer.trajet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * FIX #7a: moved to correct package com.moussefer.trajet
 * FIX #7b: @EnableCaching added — without it @Cacheable/@CacheEvict are silently ignored
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableCaching
public class TrajetServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrajetServiceApplication.class, args);
    }
}
