package com.moussefer.demande;

import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableScheduling
@EnableDiscoveryClient
public class DemandeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemandeServiceApplication.class, args);
    }
}