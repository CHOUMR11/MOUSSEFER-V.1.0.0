package com.moussefer.reservation.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    @Value("${trajet.service.url:http://trajet-service:8083}")
    private String trajetServiceUrl;

    @Value("${payment.service.url:http://payment-service:8085}")
    private String paymentServiceUrl;

    @Bean
    public WebClient trajetServiceWebClient() {
        return createWebClient(trajetServiceUrl, "trajet-service");
    }

    @Bean
    public WebClient paymentServiceWebClient() {
        return createWebClient(paymentServiceUrl, "payment-service");
    }

    private WebClient createWebClient(String baseUrl, String poolName) {
        ConnectionProvider provider = ConnectionProvider.builder(poolName)
                .maxConnections(10)
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}