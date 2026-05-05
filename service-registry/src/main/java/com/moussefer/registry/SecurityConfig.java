package com.moussefer.registry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Configuration de sécurité pour le développement local.
     * Désactive l'authentification sur tous les endpoints (Eureka, actuator, etc.)
     * pour permettre l'enregistrement des services sans erreur 401.
     *
     * ⚠️ À réactiver en production avec une authentification forte.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .httpBasic(httpBasic -> {});
        return http.build();
    }
}