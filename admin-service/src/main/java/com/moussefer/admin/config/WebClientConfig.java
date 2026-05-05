package com.moussefer.admin.config;

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

    @Value("${trajet.service.url:http://trajet-service:8083}")
    private String trajetServiceUrl;

    @Value("${reservation.service.url:http://reservation-service:8084}")
    private String reservationServiceUrl;

    @Value("${payment.service.url:http://payment-service:8085}")
    private String paymentServiceUrl;

    @Value("${banner.service.url:http://banner-service:8094}")
    private String bannerServiceUrl;

    @Value("${analytics.service.url:http://analytics-service:8092}")
    private String analyticsServiceUrl;

    @Value("${notification.service.url:http://notification-service:8086}")
    private String notificationServiceUrl;

    @Value("${auth.service.url:http://auth-service:8081}")
    private String authServiceUrl;

    @Value("${station.service.url:http://station-service:8091}")
    private String stationServiceUrl;

    @Value("${internal.api-key:dev-internal-key}")
    private String internalApiKey;

    private WebClient build(String baseUrl, String poolName) {
        ConnectionProvider provider = ConnectionProvider.builder(poolName)
                .maxConnections(10)
                .pendingAcquireTimeout(Duration.ofSeconds(5))
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .responseTimeout(Duration.ofSeconds(5));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Internal-Secret", internalApiKey)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean public WebClient userServiceWebClient()        { return build(userServiceUrl, "user-service"); }
    @Bean public WebClient trajetServiceWebClient()      { return build(trajetServiceUrl, "trajet-service"); }
    @Bean public WebClient reservationServiceWebClient() { return build(reservationServiceUrl, "reservation-service"); }
    @Bean public WebClient paymentServiceWebClient()     { return build(paymentServiceUrl, "payment-service"); }
    @Bean public WebClient bannerServiceWebClient()      { return build(bannerServiceUrl, "banner-service"); }
    @Bean public WebClient analyticsServiceWebClient()   { return build(analyticsServiceUrl, "analytics-service"); }
    @Bean public WebClient notificationServiceWebClient(){ return build(notificationServiceUrl, "notification-service"); }
    @Bean public WebClient authServiceWebClient()        { return build(authServiceUrl, "auth-service"); }
    @Bean public WebClient stationServiceWebClient()     { return build(stationServiceUrl, "station-service"); }
}
