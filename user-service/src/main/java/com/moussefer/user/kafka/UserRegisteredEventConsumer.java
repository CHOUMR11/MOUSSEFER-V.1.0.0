package com.moussefer.user.kafka;

import com.moussefer.user.entity.UserProfile;
import com.moussefer.user.entity.UserRole;
import com.moussefer.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventConsumer {

    private final UserProfileRepository userProfileRepository;

    @KafkaListener(topics = "user.registered", groupId = "user-service-group")
    @Transactional
    public void onUserRegistered(Map<String, Object> event) {
        String userId = (String) event.get("userId");
        String email = (String) event.get("email");
        String phoneNumber = (String) event.get("phoneNumber");
        String roleStr = (String) event.get("role");

        if (userId == null || email == null || roleStr == null) {
            log.warn("Invalid user.registered event: missing fields {}", event);
            return;
        }

        if (userProfileRepository.existsById(userId)) {
            log.info("User profile already exists for id: {}", userId);
            return;
        }

        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            log.error("Invalid role in event: {}", roleStr);
            return;
        }

        UserProfile profile = UserProfile.builder()
                .userId(userId)
                .email(email)
                .phoneNumber(phoneNumber)
                .role(role)
                .active(true)
                .averageRating(0.0)
                .totalTrips(0)
                .build();

        userProfileRepository.save(profile);
        log.info("Created user profile for id={}, role={}", userId, role);
    }
}