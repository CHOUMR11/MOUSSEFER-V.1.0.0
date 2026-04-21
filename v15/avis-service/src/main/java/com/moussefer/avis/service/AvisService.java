package com.moussefer.avis.service;

import com.moussefer.avis.dto.DriverRatingUpdatedEvent;
import com.moussefer.avis.entity.Avis;
import com.moussefer.avis.exception.BusinessException;
import com.moussefer.avis.exception.ResourceNotFoundException;
import com.moussefer.avis.kafka.AvisEventPublisher;
import com.moussefer.avis.repository.AvisRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AvisService {

    private final AvisRepository repository;
    private final AvisEventPublisher eventPublisher;
    private final WebClient reservationServiceWebClient;

    // ==================== CRÉATION ====================
    @Transactional
    public Avis submit(String passengerId, String driverId, String trajetId,
                       String reservationId, int rating, String comment) {
        // 1. Validation de la note
        if (rating < 1 || rating > 5) {
            throw new BusinessException("Rating must be between 1 and 5");
        }

        // 2. Vérification d'unicité (par réservation ou par triplet passager/chauffeur/trajet)
        if (reservationId != null && repository.existsByReservationId(reservationId)) {
            throw new BusinessException("You have already rated this reservation");
        }
        if (repository.existsByDriverIdAndPassengerIdAndTrajetId(driverId, passengerId, trajetId)) {
            throw new BusinessException("You have already rated this trip");
        }

        // 3. Vérification que le passager a bien effectué ce trajet
        if (!hasConfirmedReservation(passengerId, driverId, trajetId, reservationId)) {
            throw new BusinessException("You cannot rate this driver because you have not completed this trip.");
        }

        // 4. Création de l'avis
        Avis avis = Avis.builder()
                .passengerId(passengerId)
                .driverId(driverId)
                .trajetId(trajetId)
                .reservationId(reservationId)
                .rating(rating)
                .comment(comment)
                .build();
        avis = repository.save(avis);

        // 5. Recalcul de la moyenne et publication de l'événement
        updateDriverRatingAndPublish(driverId, rating);

        log.info("Avis submitted: driverId={}, rating={}, reservationId={}", driverId, rating, reservationId);
        return avis;
    }

    // ==================== LECTURE ====================
    @Transactional(readOnly = true)
    public Avis getById(String avisId) {
        return repository.findById(avisId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis not found: " + avisId));
    }

    @Transactional(readOnly = true)
    public List<Avis> getForDriver(String driverId) {
        return repository.findByDriverIdOrderByCreatedAtDesc(driverId);
    }

    @Transactional(readOnly = true)
    public Optional<Avis> getForReservation(String reservationId) {
        return repository.findByReservationId(reservationId);
    }

    // ==================== MISE À JOUR ====================
    @Transactional
    public Avis updateAvis(String passengerId, String avisId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("Rating must be between 1 and 5");
        }

        Avis avis = repository.findById(avisId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis not found: " + avisId));

        // Seul le passager auteur peut modifier son avis
        if (!avis.getPassengerId().equals(passengerId)) {
            throw new BusinessException("You can only update your own reviews");
        }

        avis.setRating(rating);
        if (comment != null) {
            avis.setComment(comment);
        }
        avis = repository.save(avis);

        // Recalcul et publication
        updateDriverRatingAndPublish(avis.getDriverId(), rating);

        log.info("Avis {} updated by passenger {}", avisId, passengerId);
        return avis;
    }

    // ==================== SUPPRESSION ====================
    @Transactional
    public void deleteAvis(String avisId) {
        Avis avis = repository.findById(avisId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis not found: " + avisId));
        String driverId = avis.getDriverId();

        repository.deleteById(avisId);
        log.info("Avis {} deleted", avisId);

        // Recalcul après suppression (pas de dernière note)
        Double newAverage = repository.computeAverageRating(driverId);
        long totalReviews = repository.countByDriver(driverId);
        if (newAverage == null) newAverage = 0.0;

        DriverRatingUpdatedEvent event = new DriverRatingUpdatedEvent(driverId, newAverage, totalReviews, 0);
        eventPublisher.publishDriverRatingUpdated(event);
        log.info("Driver rating recalculated after deletion: driverId={}, newAvg={}, totalReviews={}",
                driverId, newAverage, totalReviews);
    }

    // ==================== MÉTHODES PRIVÉES ====================
    private void updateDriverRatingAndPublish(String driverId, int lastRating) {
        Double newAverage = repository.computeAverageRating(driverId);
        if (newAverage == null) newAverage = (double) lastRating;
        long totalReviews = repository.countByDriver(driverId);

        DriverRatingUpdatedEvent event = new DriverRatingUpdatedEvent(driverId, newAverage, totalReviews, lastRating);
        eventPublisher.publishDriverRatingUpdated(event);

        log.debug("Driver rating updated: driverId={}, newAvg={}, totalReviews={}", driverId, newAverage, totalReviews);
    }

    @Retry(name = "reservationService", fallbackMethod = "fallbackHasConfirmedReservation")
    private boolean hasConfirmedReservation(String passengerId, String driverId, String trajetId, String reservationId) {
        // Priorité : si reservationId est fourni, on l'utilise directement
        String uriPath = "/api/v1/reservations/internal/check";
        var uriBuilder = reservationServiceWebClient.get()
                .uri(uriBuilderFactory -> {
                    var builder = uriBuilderFactory
                            .path(uriPath)
                            .queryParam("passengerId", passengerId);
                    if (reservationId != null && !reservationId.isBlank()) {
                        builder.queryParam("reservationId", reservationId);
                    } else {
                        builder.queryParam("driverId", driverId)
                                .queryParam("trajetId", trajetId);
                    }
                    return builder.build();
                });

        Boolean confirmed = uriBuilder
                .retrieve()
                .bodyToMono(Boolean.class)
                .block(Duration.ofSeconds(5));

        return Boolean.TRUE.equals(confirmed);
    }

    @SuppressWarnings("unused")
    private boolean fallbackHasConfirmedReservation(String passengerId, String driverId, String trajetId,
                                                    String reservationId, Throwable t) {
        log.error("Reservation service unavailable for rating check. Denying rating. " +
                        "passengerId={}, driverId={}, trajetId={}, reservationId={}",
                passengerId, driverId, trajetId, reservationId, t);
        return false; // fail-closed : on refuse la notation
    }
}