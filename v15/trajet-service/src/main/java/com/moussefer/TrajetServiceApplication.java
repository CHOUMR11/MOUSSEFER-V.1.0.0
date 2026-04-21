package com.moussefer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableCaching   // ✅ added
public class TrajetServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrajetServiceApplication.class, args);
    }
}