package com.moussefer.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class OutboxSchedulerConfig {
    // Active le scheduling pour OutboxService
}