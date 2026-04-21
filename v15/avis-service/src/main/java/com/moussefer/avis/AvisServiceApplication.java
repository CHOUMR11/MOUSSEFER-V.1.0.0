package com.moussefer.avis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class AvisServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AvisServiceApplication.class, args);
    }
}