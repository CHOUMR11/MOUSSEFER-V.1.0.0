package com.moussefer.auth.config;

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

    @Value("${user.service.url:http://user-service:8082}")
    private String userServiceUrl;

    @Value("${admin.service.url:http://admin-service:8093}")
    private String adminServiceUrl;

    @Value("${internal.api-key}")
    private String internalApiKey;

    private WebClient buildWebClient(String baseUrl, String poolName) {
        ConnectionProvider provider = ConnectionProvider.builder(poolName)
                .maxConnections(10)
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(5));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Secret", internalApiKey)   // ✅ auto-add internal secret
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient userServiceWebClient() {
        return buildWebClient(userServiceUrl, "user-service");
    }

    @Bean
    public WebClient adminServiceWebClient() {
        return buildWebClient(adminServiceUrl, "admin-service");
    }
}