package com.moussefer.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moussefer.payment.entity.OutboxEvent;
import com.moussefer.payment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByProcessedAtIsNull();
        for (OutboxEvent event : events) {
            try {
                Object payload = objectMapper.readValue(event.getPayload(), Object.class);
                kafkaTemplate.send(event.getEventType(), event.getAggregateId(), payload);
                outboxEventRepository.markAsProcessed(event.getId());
                log.info("Outbox event processed: {}", event.getId());
            } catch (Exception e) {
                log.error("Failed to process outbox event: {}", event.getId(), e);
            }
        }
    }
}