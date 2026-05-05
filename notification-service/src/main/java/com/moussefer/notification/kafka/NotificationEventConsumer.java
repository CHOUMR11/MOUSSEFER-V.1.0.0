package com.moussefer.notification.kafka;

import com.moussefer.notification.dto.DriverInfoResponse;
import com.moussefer.notification.entity.NotificationType;
import com.moussefer.notification.service.NotificationService;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final WebClient userServiceWebClient;
    private final WebClient demandeServiceWebClient;

    @Value("${internal.api-key}")
    private String internalApiKey;

    @Value("${notification.admin.user-id}")
    private String adminUserId;

    // -------------------- Helpers --------------------
    private static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val instanceof String s ? s : null;
    }

    private static String getEmail(Map<String, Object> map) {
        Object userObj = map.get("user");
        if (userObj instanceof Map<?, ?> userMap) {
            Object email = userMap.get("email");
            return email instanceof String s ? s : null;
        }
        return getString(map, "email");
    }

    // -------------------- Event Consumers --------------------

    @KafkaListener(topics = "user.registered", groupId = "notification-service-group")
    public void onUserRegistered(Map<String, Object> event) {
        String userId = getString(event, "userId");
        String email = getEmail(event);
        if (userId == null) return;
        notificationService.send(userId, "Bienvenue sur Moussefer !",
                "Votre compte a été créé avec succès. Explorez les trajets disponibles.",
                NotificationType.EMAIL, userId, "USER", email);
    }

    // V22 — Password reset emails
    @KafkaListener(topics = "auth.password_reset_requested", groupId = "notification-service-group")
    public void onPasswordResetRequested(Map<String, Object> event) {
        String userId = getString(event, "userId");
        String email  = getString(event, "email");
        String resetUrl = getString(event, "resetUrl");
        Object ttlObj = event.get("expiresInMinutes");
        String ttl = ttlObj != null ? ttlObj.toString() : "60";
        if (userId == null || email == null || resetUrl == null) {
            log.warn("Password reset event missing fields: {}", event);
            return;
        }
        String body = String.format(
                "Bonjour,\n\nVous avez demandé la réinitialisation de votre mot de passe.\n\n" +
                        "Cliquez sur le lien suivant pour choisir un nouveau mot de passe :\n%s\n\n" +
                        "Ce lien expire dans %s minutes.\n\n" +
                        "Si vous n'avez pas fait cette demande, ignorez simplement cet email — " +
                        "votre mot de passe actuel reste actif.\n\n" +
                        "L'équipe Moussefer",
                resetUrl, ttl);
        notificationService.send(userId, "Réinitialisation de mot de passe Moussefer",
                body, NotificationType.EMAIL, userId, "USER", email);
        log.info("Password reset email dispatched to user={}", userId);
    }

    @KafkaListener(topics = "auth.password_reset_completed", groupId = "notification-service-group")
    public void onPasswordResetCompleted(Map<String, Object> event) {
        String userId = getString(event, "userId");
        String email  = getString(event, "email");
        if (userId == null || email == null) return;
        notificationService.send(userId, "Votre mot de passe a été modifié",
                "Bonjour,\n\nVotre mot de passe Moussefer a été modifié avec succès.\n\n" +
                        "Si vous n'êtes pas à l'origine de cette modification, contactez " +
                        "immédiatement notre support.\n\nL'équipe Moussefer",
                NotificationType.EMAIL, userId, "USER", email);
    }

    @KafkaListener(topics = "trajet.published", groupId = "notification-service-group")
    public void onTrajetPublished(Map<String, Object> event) {
        String driverId = getString(event, "driverId");
        if (driverId == null) return;

        notificationService.send(driverId, "Trajet publié",
                String.format("Votre trajet de %s à %s le %s est maintenant visible.",
                        event.get("departureCity"), event.get("arrivalCity"), event.get("departureDate")),
                NotificationType.IN_APP, getString(event, "trajetId"), "TRAJET", null);

        // Notifier les passagers en attente sur cette route (via demande-service)
        notifyWaitingPassengers(
                getString(event, "departureCity"),
                getString(event, "arrivalCity"),
                getString(event, "trajetId"),
                getString(event, "departureDate"),
                event.get("availableSeats")
        );

        // UC-31: Notifier les abonnés aux alertes de disponibilité
        try {
            int seats = event.get("availableSeats") instanceof Number n ? n.intValue() : 4;
            notificationService.notifyAlertSubscribers(
                    getString(event, "departureCity"),
                    getString(event, "arrivalCity"),
                    getString(event, "trajetId"),
                    getString(event, "departureDate"),
                    seats
            );
        } catch (Exception e) {
            log.warn("Failed to notify alert subscribers: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "reservation.created", groupId = "notification-service-group")
    public void onReservationCreated(Map<String, Object> event) {
        String driverId = getString(event, "driverId");
        if (driverId == null) return;
        notificationService.send(driverId, "Nouvelle demande de réservation",
                String.format("Un passager demande %s place(s) pour votre trajet.", event.get("seatsReserved")),
                NotificationType.IN_APP, getString(event, "reservationId"), "RESERVATION", null);
    }

    @KafkaListener(topics = "reservation.accepted", groupId = "notification-service-group")
    public void onReservationAccepted(Map<String, Object> event) {
        String passengerId = getString(event, "passengerId");
        if (passengerId == null) return;
        notificationService.send(passengerId, "Réservation acceptée !",
                "Le chauffeur a accepté votre demande. Procédez au paiement.",
                NotificationType.IN_APP, getString(event, "reservationId"), "RESERVATION", null);
    }

    @KafkaListener(topics = "reservation.refused", groupId = "notification-service-group")
    public void onReservationRefused(Map<String, Object> event) {
        String passengerId = getString(event, "passengerId");
        if (passengerId == null) return;
        notificationService.send(passengerId, "Réservation refusée",
                "Le chauffeur a refusé votre demande. Vous pouvez chercher un autre trajet.",
                NotificationType.IN_APP, getString(event, "reservationId"), "RESERVATION", null);
    }

    @KafkaListener(topics = "reservation.confirmed", groupId = "notification-service-group")
    public void onReservationConfirmed(Map<String, Object> event) {
        String passengerId = getString(event, "passengerId");
        if (passengerId == null) return;
        notificationService.send(passengerId, "Paiement confirmé — Bon voyage !",
                "Votre réservation est confirmée. Le chat avec le chauffeur est maintenant actif.",
                NotificationType.IN_APP, getString(event, "reservationId"), "RESERVATION", null);
    }

    @KafkaListener(topics = "reservation.driver.reminder", groupId = "notification-service-group")
    public void onDriverReminder(Map<String, Object> event) {
        String driverId = getString(event, "driverId");
        if (driverId == null) return;
        notificationService.send(driverId, "⚠️ Rappel : répondez à la demande",
                "Vous avez 10 minutes pour accepter ou refuser la demande. Passé ce délai, le trajet suivant sera activé.",
                NotificationType.IN_APP, getString(event, "reservationId"), "RESERVATION", null);
    }

    @KafkaListener(topics = "reservation.admin.alert", groupId = "notification-service-group")
    public void onAdminAlert(Map<String, Object> event) {
        notificationService.send(adminUserId, "⏱️ Alerte : réponse chauffeur en attente (8 min)",
                String.format("Le chauffeur %s n'a toujours pas répondu à la réservation %s. Escalade automatique dans 7 minutes.",
                        event.get("driverId"), event.get("reservationId")),
                NotificationType.IN_APP, getString(event, "reservationId"), "ADMIN_ALERT", null);
    }

    @KafkaListener(topics = "reservation.escalated", groupId = "notification-service-group")
    public void onEscalated(Map<String, Object> event) {
        notificationService.send(adminUserId, "⚠️ Escalade automatique",
                String.format("Chauffeur %s n'a pas répondu. Trajet suivant activé.", event.get("driverId")),
                NotificationType.IN_APP, getString(event, "reservationId"), "ESCALATION", null);
    }

    @KafkaListener(topics = "payment.confirmed", groupId = "notification-service-group")
    public void onPaymentConfirmed(Map<String, Object> event) {
        String passengerId = getString(event, "passengerId");
        if (passengerId == null) return;
        notificationService.send(passengerId, "Facture disponible",
                "Votre ticket a été généré. Consultez votre historique pour le télécharger.",
                NotificationType.IN_APP, getString(event, "reservationId"), "PAYMENT", null);
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service-group")
    public void onPaymentFailed(Map<String, Object> event) {
        String reservationId = getString(event, "reservationId");
        String reason = getString(event, "reason");
        if (reservationId == null) return;
        // Notify via admin — passenger ID not always available in payment.failed
        notificationService.send(adminUserId, "⚠️ Paiement échoué",
                String.format("Le paiement pour la réservation %s a échoué. Raison : %s",
                        reservationId, reason != null ? reason : "Inconnue"),
                NotificationType.IN_APP, reservationId, "PAYMENT_FAILED", null);
    }

    @KafkaListener(topics = "trajet.activated", groupId = "notification-service-group")
    public void onTrajetActivated(Map<String, Object> event) {
        String driverId = getString(event, "driverId");
        if (driverId == null) return;
        notificationService.send(driverId, "🚗 Votre trajet est maintenant actif !",
                String.format("Votre trajet %s → %s est désormais en première position et réservable par les passagers.",
                        event.get("departureCity"), event.get("arrivalCity")),
                NotificationType.IN_APP, getString(event, "trajetId"), "TRAJET", null);
    }

    @KafkaListener(topics = "voyage.payment.confirmed", groupId = "notification-service-group")
    public void onVoyagePaymentConfirmed(Map<String, Object> event) {
        String passengerId = getString(event, "passengerId");
        if (passengerId == null) return;

        fetchUserEmail(passengerId)
                .subscribe(email -> {
                    String body = String.format(
                            "Votre paiement pour le voyage %s → %s le %s est confirmé. Montant : %s€. Réservation : %s. Bon voyage !",
                            event.get("departureCity"), event.get("arrivalCity"),
                            event.get("departureDate"), event.get("totalPrice"), event.get("reservationId"));

                    notificationService.send(passengerId, "✅ Confirmation de paiement — Voyage organisé",
                            body, NotificationType.EMAIL, getString(event, "reservationId"), "VOYAGE_PAYMENT", email);
                });
    }

    @KafkaListener(topics = "trajet.cancelled", groupId = "notification-service-group")
    public void onTrajetCancelled(Map<String, Object> event) {
        String driverId = getString(event, "driverId");
        String trajetId = getString(event, "trajetId");
        String departureCity = getString(event, "departureCity");
        String arrivalCity = getString(event, "arrivalCity");

        if (driverId != null) {
            notificationService.send(driverId, "Trajet annulé",
                    String.format("Votre trajet %s → %s a été annulé.", departureCity, arrivalCity),
                    NotificationType.IN_APP, trajetId, "TRAJET", null);
        }

        notificationService.send(adminUserId, "Trajet annulé — réservations impactées",
                String.format("Le trajet %s (%s → %s) a été annulé. Les réservations associées seront automatiquement annulées.",
                        trajetId, departureCity, arrivalCity),
                NotificationType.IN_APP, trajetId, "TRAJET_CANCELLED", null);
    }

    @KafkaListener(topics = "demande.threshold.reached", groupId = "notification-service-group")
    public void onDemandeThresholdReached(Map<String, Object> event) {
        String departureCity = getString(event, "departureCity");
        String arrivalCity = getString(event, "arrivalCity");
        String requestedDate = getString(event, "requestedDate");
        Object totalSeatsObj = event.get("totalSeatsReserved");
        Object thresholdObj = event.get("thresholdUsed");

        if (departureCity == null || arrivalCity == null) {
            log.warn("demande.threshold.reached missing route info: {}", event);
            return;
        }

        getDriverIdsByRoute(departureCity, arrivalCity)
                .subscribe(driverIds -> {
                    String message = String.format("📢 Demande collective pour %s → %s le %s a atteint %s/%s places réservées.",
                            departureCity, arrivalCity, requestedDate, totalSeatsObj, thresholdObj);
                    for (String driverId : driverIds) {
                        notificationService.send(driverId, "Nouvelle demande collective sur votre route",
                                message, NotificationType.IN_APP, getString(event, "demandeId"), "DEMANDE", null);
                    }
                    log.info("Notified {} drivers for demande.threshold.reached", driverIds.size());
                });
    }

    @KafkaListener(topics = "avis.driver.rating.updated", groupId = "notification-service-group")
    public void onDriverRatingUpdated(Map<String, Object> event) {
        String driverId = getString(event, "driverId");
        if (driverId == null) return;
        notificationService.send(driverId, "⭐ Votre note a changé",
                String.format("Nouvelle moyenne : %.2f / 5 basée sur %d avis.",
                        event.get("averageRating"), event.get("totalReviews")),
                NotificationType.IN_APP, driverId, "RATING", null);
    }

    @KafkaListener(topics = "voyage.cancelled.refund", groupId = "notification-service-group")
    public void onVoyageCancelledRefund(Map<String, Object> event) {
        String passengerId = getString(event, "passengerId");
        if (passengerId == null) return;
        notificationService.send(passengerId, "Voyage annulé — remboursement en cours",
                String.format("Le voyage a été annulé. Un remboursement de %s€ est en cours de traitement.",
                        event.get("totalPrice")),
                NotificationType.IN_APP, getString(event, "reservationId"), "VOYAGE_REFUND", null);
    }

    // -------------------- Non‑bloquants réactifs --------------------
    private void notifyWaitingPassengers(String departureCity, String arrivalCity,
                                         String trajetId, String departureDate, Object availableSeats) {
        if (departureCity == null || arrivalCity == null) return;

        demandeServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/demandes/internal/waiting-passengers")
                        .queryParam("departureCity", departureCity)
                        .queryParam("arrivalCity", arrivalCity)
                        .build())
                .header("X-Internal-Secret", internalApiKey)   // ← added missing secret
                .retrieve()
                .bodyToMono(String[].class)
                .timeout(Duration.ofSeconds(5))
                .subscribe(
                        passengerIds -> {
                            if (passengerIds != null && passengerIds.length > 0) {
                                String message = String.format("Un nouveau trajet %s → %s le %s est disponible avec %s place(s) !",
                                        departureCity, arrivalCity, departureDate, availableSeats);
                                for (String passengerId : passengerIds) {
                                    notificationService.send(passengerId, "🚗 Trajet disponible sur votre route !",
                                            message, NotificationType.IN_APP, trajetId, "TRAJET_MATCH", null);
                                }
                                log.info("Notified {} waiting passengers for route {}→{}", passengerIds.length, departureCity, arrivalCity);
                            }
                        },
                        error -> log.error("Failed to notify waiting passengers for route {}→{}: {}", departureCity, arrivalCity, error.getMessage())
                );
    }

    private Mono<String> fetchUserEmail(String userId) {
        return userServiceWebClient.get()
                .uri("/api/v1/users/internal/{id}", userId)
                .header("X-Internal-Secret", internalApiKey)
                .retrieve()
                .bodyToMono(DriverInfoResponse.class)
                .timeout(Duration.ofSeconds(5))
                .map(DriverInfoResponse::getEmail)
                .onErrorResume(e -> {
                    log.error("Failed to fetch email for user {}", userId, e);
                    return Mono.empty();
                });
    }

    @Retry(name = "userService")
    private Mono<List<String>> getDriverIdsByRoute(String departureCity, String arrivalCity) {
        return userServiceWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/internal/drivers/by-route")
                        .queryParam("departureCity", departureCity)
                        .queryParam("arrivalCity", arrivalCity)
                        .build())
                .header("X-Internal-Secret", internalApiKey)
                .retrieve()
                .bodyToFlux(DriverInfoResponse.class)
                .collectList()
                .timeout(Duration.ofSeconds(5))
                .map(drivers -> drivers.stream().map(DriverInfoResponse::getUserId).toList())
                .onErrorResume(e -> {
                    log.error("Failed to fetch drivers for route {}→{}", departureCity, arrivalCity, e);
                    return Mono.just(List.of());
                });
    }
}