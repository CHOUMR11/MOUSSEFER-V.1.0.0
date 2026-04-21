package com.moussefer.banner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class BannerServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BannerServiceApplication.class, args);
    }
}
