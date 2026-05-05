package com.moussefer.avis.service;

import com.moussefer.avis.dto.DriverRatingUpdatedEvent;
import com.moussefer.avis.entity.Avis;
import com.moussefer.avis.exception.BusinessException;
import com.moussefer.avis.exception.ResourceNotFoundException;
import com.moussefer.avis.kafka.AvisEventPublisher;
import com.moussefer.avis.repository.AvisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${internal.api-key}")
    private String internalApiKey;

    // ==================== CRÉATION ====================
    @Transactional
    public Avis submit(String passengerId, String driverId, String trajetId,
                       String reservationId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new BusinessException("Rating must be between 1 and 5");
        }

        if (reservationId != null && repository.existsByReservationId(reservationId)) {
            throw new BusinessException("You have already rated this reservation");
        }
        if (repository.existsByDriverIdAndPassengerIdAndTrajetId(driverId, passengerId, trajetId)) {
            throw new BusinessException("You have already rated this trip");
        }

        if (!hasConfirmedReservation(passengerId, driverId, trajetId, reservationId)) {
            throw new BusinessException("You cannot rate this driver because you have not completed this trip.");
        }

        Avis avis = Avis.builder()
                .passengerId(passengerId)
                .driverId(driverId)
                .trajetId(trajetId)
                .reservationId(reservationId)
                .rating(rating)
                .comment(comment)
                .build();
        avis = repository.save(avis);

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

        if (!avis.getPassengerId().equals(passengerId)) {
            throw new BusinessException("You can only update your own reviews");
        }

        avis.setRating(rating);
        if (comment != null) {
            avis.setComment(comment);
        }
        avis = repository.save(avis);

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

    private boolean hasConfirmedReservation(String passengerId, String driverId, String trajetId, String reservationId) {
        try {
            Boolean confirmed = reservationServiceWebClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/api/v1/reservations/internal/check")
                                .queryParam("passengerId", passengerId);
                        if (reservationId != null && !reservationId.isBlank()) {
                            builder.queryParam("reservationId", reservationId);
                        } else {
                            builder.queryParam("driverId", driverId)
                                    .queryParam("trajetId", trajetId);
                        }
                        return builder.build();
                    })
                    .header("X-Internal-Secret", internalApiKey)   // ← added
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block(Duration.ofSeconds(5));

            return Boolean.TRUE.equals(confirmed);
        } catch (Exception e) {
            log.error("Reservation service unavailable for rating check. Denying rating. " +
                            "passengerId={}, driverId={}, trajetId={}, reservationId={}",
                    passengerId, driverId, trajetId, reservationId, e);
            return false; // fail-closed
        }
    }
}