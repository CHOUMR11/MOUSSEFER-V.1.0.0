package com.moussefer.notification.service;

import com.moussefer.notification.entity.AlertSubscription;
import com.moussefer.notification.entity.Notification;
import com.moussefer.notification.entity.NotificationType;
import com.moussefer.notification.repository.AlertSubscriptionRepository;
import com.moussefer.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final AlertSubscriptionRepository alertRepository;
    private final JavaMailSender mailSender;

    @Transactional
    public void send(String userId, String title, String body, NotificationType type,
                     String referenceId, String referenceType, String email) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .body(body)
                .type(type)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
        repository.save(notification);

        if (type == NotificationType.EMAIL && email != null && !email.isBlank()) {
            sendEmail(email, title, body);
        }

        log.info("Notification sent: userId={}, type={}, title={}", userId, type, title);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(String userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Notification> notifications = repository.findByUserIdOrderBySentAtDesc(userId, PageRequest.of(Math.max(page, 0), safeSize))
                .getContent();
        log.debug("Fetched {} notifications for userId={}, page={}", notifications.size(), userId, page);
        return notifications;
    }

    @Transactional
    public void markAllRead(String userId) {
        repository.markAllAsRead(userId);
        log.info("All notifications marked as read for userId={}", userId);
    }

    @Transactional
    public boolean markOneRead(String notificationId, String userId) {
        int updated = repository.markOneAsRead(notificationId, userId);
        if (updated == 0) {
            log.warn("Failed to mark read: notificationId={} not found or not owned by userId={}", notificationId, userId);
            return false;
        }
        log.info("Notification {} marked as read by user {}", notificationId, userId);
        return true;
    }

    @Transactional
    public boolean deleteOneNotification(String notificationId, String userId) {
        int deleted = repository.deleteOneByIdAndUserId(notificationId, userId);
        if (deleted == 0) {
            log.warn("Failed to delete: notificationId={} not found or not owned by userId={}", notificationId, userId);
            return false;
        }
        log.info("Notification {} deleted by user {}", notificationId, userId);
        return true;
    }

    @Transactional
    public void deleteAllNotifications(String userId) {
        repository.deleteAllByUserId(userId);
        log.info("All notifications deleted for userId={}", userId);
    }

    private void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Email delivery failed for {}: {}", to, e.getMessage());
        }
    }

    // ─── Availability Alert Subscriptions (UC-31) ────────────────────
    @Transactional
    public AlertSubscription subscribeToAlert(String userId, String departureCity, String arrivalCity,
                                               String desiredDate, int minSeats) {
        if (alertRepository.existsByUserIdAndDepartureCityAndArrivalCityAndActiveTrue(userId, departureCity, arrivalCity)) {
            throw new RuntimeException("You already have an active alert for this route");
        }
        AlertSubscription alert = AlertSubscription.builder()
                .userId(userId)
                .departureCity(departureCity)
                .arrivalCity(arrivalCity)
                .desiredDate(desiredDate != null ? LocalDate.parse(desiredDate) : null)
                .minSeats(Math.max(minSeats, 1))
                .build();
        alert = alertRepository.save(alert);
        log.info("Alert subscription created: userId={}, route={}→{}", userId, departureCity, arrivalCity);
        return alert;
    }

    @Transactional(readOnly = true)
    public List<AlertSubscription> getMyActiveAlerts(String userId) {
        return alertRepository.findByUserIdAndActiveTrue(userId);
    }

    @Transactional
    public void unsubscribeAlert(String alertId, String userId) {
        AlertSubscription alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null || !alert.getUserId().equals(userId)) return;
        alert.setActive(false);
        alertRepository.save(alert);
        log.info("Alert {} deactivated by user {}", alertId, userId);
    }

    @Transactional
    public void notifyAlertSubscribers(String departureCity, String arrivalCity, String trajetId,
                                        String departureDate, int availableSeats) {
        List<AlertSubscription> alerts = alertRepository
                .findByDepartureCityAndArrivalCityAndActiveTrue(departureCity, arrivalCity);
        int notified = 0;
        for (AlertSubscription alert : alerts) {
            if (availableSeats < alert.getMinSeats()) continue;
            if (alert.isNotified()) continue;
            send(alert.getUserId(), "🚗 Trajet disponible !",
                    String.format("Un trajet %s → %s le %s avec %d place(s) est disponible. Réservez maintenant !",
                            departureCity, arrivalCity, departureDate, availableSeats),
                    NotificationType.IN_APP, trajetId, "TRAJET_ALERT", null);
            alert.setNotified(true);
            alert.setActive(false);
            alertRepository.save(alert);
            notified++;
        }
        if (notified > 0) {
            log.info("Notified {} alert subscribers for route {}→{}", notified, departureCity, arrivalCity);
        }
    }
}