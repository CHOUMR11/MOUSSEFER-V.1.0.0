package com.moussefer.avis.kafka;

import com.moussefer.avis.dto.DriverRatingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AvisEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "avis.driver.rating.updated";

    public void publishDriverRatingUpdated(DriverRatingUpdatedEvent event) {
        kafkaTemplate.send(TOPIC, event.getDriverId(), event);
        log.info("Published event: topic={}, driverId={}, avgRating={}, totalReviews={}",
                TOPIC, event.getDriverId(), event.getAverageRating(), event.getTotalReviews());
    }
}