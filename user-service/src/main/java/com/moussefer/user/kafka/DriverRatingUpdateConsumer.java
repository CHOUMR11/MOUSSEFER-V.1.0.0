package com.moussefer.user.kafka;

import com.moussefer.user.event.DriverRatingUpdatedEvent;
import com.moussefer.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverRatingUpdateConsumer {

    private final UserService userService;

    @KafkaListener(topics = "avis.driver.rating.updated", groupId = "user-service-group")
    public void onDriverRatingUpdated(DriverRatingUpdatedEvent event) {
        if (event.getDriverId() == null || event.getAverageRating() == null || event.getTotalReviews() == null) {
            log.warn("Invalid rating update event: {}", event);
            return;
        }
        userService.updateDriverRating(
                event.getDriverId(),
                event.getAverageRating(),
                event.getTotalReviews()
        );
        log.info("Driver rating updated: driverId={}, avg={}, trips={}",
                event.getDriverId(), event.getAverageRating(), event.getTotalReviews());
    }
}