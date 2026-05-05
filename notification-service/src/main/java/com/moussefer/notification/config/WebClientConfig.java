package com.moussefer.notification.config;

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

    @Value("${user.service.url}")
    private String userServiceUrl;

    @Value("${demande.service.url}")
    private String demandeServiceUrl;

    @Bean
    public WebClient userServiceWebClient() {
        return createWebClient(userServiceUrl, "user-service");
    }

    @Bean
    public WebClient demandeServiceWebClient() {
        return createWebClient(demandeServiceUrl, "demande-service");
    }

    private WebClient createWebClient(String baseUrl, String connectionPoolName) {
        ConnectionProvider provider = ConnectionProvider.builder(connectionPoolName)
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