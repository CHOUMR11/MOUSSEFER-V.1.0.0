package com.moussefer.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${reservation.service.url:http://reservation-service:8084}")
    private String reservationServiceUrl;

    @Bean
    public WebClient reservationServiceWebClient() {
        return WebClient.builder().baseUrl(reservationServiceUrl).build();
    }
}