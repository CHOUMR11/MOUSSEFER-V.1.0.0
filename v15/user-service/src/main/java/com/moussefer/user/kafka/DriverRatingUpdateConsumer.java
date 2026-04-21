package com.moussefer.user.kafka;

import com.moussefer.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverRatingUpdateConsumer {

    private final UserService userService;

    @KafkaListener(topics = "avis.driver.rating.updated", groupId = "user-service-group")
    public void onDriverRatingUpdated(Map<String, Object> event) {
        String driverId = (String) event.get("driverId");
        Object avgObj = event.get("averageRating");
        Object totalObj = event.get("totalReviews");

        if (driverId == null || avgObj == null || totalObj == null) {
            log.warn("Invalid rating update event: {}", event);
            return;
        }

        double averageRating = ((Number) avgObj).doubleValue();
        int totalTrips = ((Number) totalObj).intValue();
        userService.updateDriverRating(driverId, averageRating, totalTrips);
        log.info("Driver rating updated: driverId={}, avg={}, trips={}", driverId, averageRating, totalTrips);
    }
}