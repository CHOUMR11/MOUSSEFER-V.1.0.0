package com.moussefer.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moussefer.reservation.dto.external.TrajetInfoResponse;
import com.moussefer.reservation.dto.request.CreateReservationRequest;
import com.moussefer.reservation.dto.response.ReservationPricingResponse;
import com.moussefer.reservation.dto.response.ReservationResponse;
import com.moussefer.reservation.entity.OutboxEvent;
import com.moussefer.reservation.entity.Reservation;
import com.moussefer.reservation.entity.ReservationStatus;
import com.moussefer.reservation.exception.BusinessException;
import com.moussefer.reservation.exception.ResourceNotFoundException;
import com.moussefer.reservation.repository.OutboxEventRepository;
import com.moussefer.reservation.repository.ReservationRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationSchedulerService reservationSchedulerService;
    private final WebClient trajetServiceWebClient;
    private final WebClient paymentServiceWebClient;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${driver.response.timeout.minutes:15}")
    private int driverTimeoutMinutes;

    @Value("${driver.reminder.minutes:5}")
    private int reminderMinutes;

    @Value("${admin.notification.minutes:8}")
    private int adminNotificationMinutes;

    @Value("${internal.api-key}")
    private String internalApiKey;

    // ──────────────────────────────────────────────────────────
    // 1. Passenger creates reservation
    // ──────────────────────────────────────────────────────────
    @Transactional
    @Retry(name = "trajetService")
    public ReservationResponse create(String passengerId, CreateReservationRequest req) {
        TrajetInfoResponse trajetInfo = trajetServiceWebClient.get()
                .uri("/api/v1/trajets/internal/{id}", req.getTrajetId())
                .header("X-Internal-Secret", internalApiKey)
                .retrieve()
                .bodyToMono(TrajetInfoResponse.class)
                .block(Duration.ofSeconds(5));

        if (trajetInfo == null) {
            throw new BusinessException("Trajet not found: " + req.getTrajetId());
        }
        if (!"ACTIVE".equals(trajetInfo.getStatus())) {
            throw new BusinessException("Trajet is not available for booking (status: " + trajetInfo.getStatus() + ")");
        }

        String driverId = trajetInfo.getDriverId();
        if (passengerId.equals(driverId)) {
            throw new BusinessException("You cannot reserve your own trajet");
        }

        boolean alreadyReserved = reservationRepository
                .existsByPassengerIdAndTrajetIdAndStatusIn(passengerId, req.getTrajetId(),
                        List.of(ReservationStatus.PENDING_DRIVER, ReservationStatus.ACCEPTED,
                                ReservationStatus.PAYMENT_PENDING, ReservationStatus.CONFIRMED));
        if (alreadyReserved) {
            throw new BusinessException("You already have an active reservation on this trajet. " +
                    "Cancel it first if you want to create a new one.");
        }

        BigDecimal pricePerSeat = trajetInfo.getPricePerSeat();
        if (pricePerSeat == null || pricePerSeat.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Invalid price for this trajet");
        }
        if (req.getSeatsReserved() > trajetInfo.getAvailableSeats()) {
            throw new BusinessException("Not enough seats available. Requested: "
                    + req.getSeatsReserved() + ", available: " + trajetInfo.getAvailableSeats());
        }

        BigDecimal totalPrice = pricePerSeat.multiply(BigDecimal.valueOf(req.getSeatsReserved()));
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(driverTimeoutMinutes);

        Reservation reservation = Reservation.builder()
                .trajetId(req.getTrajetId())
                .passengerId(passengerId)
                .driverId(driverId)
                .seatsReserved(req.getSeatsReserved())
                .totalPrice(totalPrice)
                .driverResponseDeadline(deadline)
                .departureDate(trajetInfo.getDepartureDate())
                .build();

        reservation = reservationRepository.save(reservation);
        log.info("Reservation created: id={} passenger={} trajet={}", reservation.getId(), passengerId, req.getTrajetId());

        try {
            reserveSeatsTemporarily(reservation);
        } catch (Exception e) {
            reservationRepository.delete(reservation);
            log.error("Failed to temporarily reserve seats for trajet {}. Reservation {} deleted.",
                    req.getTrajetId(), reservation.getId(), e);
            throw new BusinessException("Failed to reserve seats — please retry.");
        }

        publishEvent("reservation.created", reservation.getId(), buildEventMap(reservation, "CREATED"));
        return toResponse(reservation);
    }

    // ──────────────────────────────────────────────────────────
    // 2. Driver accepts
    // ──────────────────────────────────────────────────────────
    @Transactional
    public ReservationResponse driverAccept(String driverId, String reservationId) {
        Reservation r = findAndVerifyDriver(driverId, reservationId);
        if (r.getStatus() != ReservationStatus.PENDING_DRIVER) {
            throw new BusinessException("Cannot accept — reservation not pending");
        }
        r.setStatus(ReservationStatus.ACCEPTED);
        r.setConfirmedAt(LocalDateTime.now());
        reservationRepository.save(r);
        log.info("Reservation accepted: id={}", reservationId);
        publishEvent("reservation.accepted", reservationId, buildEventMap(r, "ACCEPTED"));
        return toResponse(r);
    }

    // ──────────────────────────────────────────────────────────
    // 3. Driver refuses
    // ──────────────────────────────────────────────────────────
    @Transactional
    public ReservationResponse driverRefuse(String driverId, String reservationId, String reason) {
        Reservation r = findAndVerifyDriver(driverId, reservationId);
        if (r.getStatus() != ReservationStatus.PENDING_DRIVER) {
            throw new BusinessException("Cannot refuse — reservation not pending");
        }
        r.setStatus(ReservationStatus.REFUSED);
        r.setRefusalReason(reason);
        r.setCancelledAt(LocalDateTime.now());
        reservationRepository.save(r);
        log.info("Reservation refused: id={} reason={}", reservationId, reason);
        releaseTemporaryReservation(r);
        publishEvent("reservation.refused", reservationId, buildEventMap(r, "REFUSED"));
        return toResponse(r);
    }

    // ──────────────────────────────────────────────────────────
    // 4. Payment confirmed (Kafka consumer)
    // ──────────────────────────────────────────────────────────
    @Transactional
    public void confirmPayment(String reservationId, String paymentIntentId) {
        Reservation r = findOrThrow(reservationId);
        if (r.getStatus() != ReservationStatus.ACCEPTED && r.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            log.warn("Payment confirmation ignored for reservation {} with status {}", reservationId, r.getStatus());
            return;
        }
        confirmSeats(r);
        r.setStatus(ReservationStatus.CONFIRMED);
        r.setPaymentIntentId(paymentIntentId);
        r.setPaidAt(LocalDateTime.now());
        reservationRepository.save(r);
        log.info("Reservation payment confirmed: id={}", reservationId);
        publishEvent("reservation.confirmed", reservationId, buildEventMap(r, "CONFIRMED"));
    }

    // ──────────────────────────────────────────────────────────
    // 4b. ACCEPTED → PAYMENT_PENDING (called by payment-service)
    // ──────────────────────────────────────────────────────────
    @Transactional
    public void markPaymentInitiated(String reservationId) {
        Reservation r = findOrThrow(reservationId);
        if (r.getStatus() != ReservationStatus.ACCEPTED) {
            log.debug("Skipping PAYMENT_PENDING for reservation {} (status={})", reservationId, r.getStatus());
            return;
        }
        r.setStatus(ReservationStatus.PAYMENT_PENDING);
        reservationRepository.save(r);
        log.info("Reservation → PAYMENT_PENDING: id={}", reservationId);
    }

    // ──────────────────────────────────────────────────────────
    // 5. Passenger cancels
    // ──────────────────────────────────────────────────────────
    @Transactional
    public void cancel(String passengerId, String reservationId) {
        Reservation r = findOrThrow(reservationId);
        if (!r.getPassengerId().equals(passengerId)) {
            throw new BusinessException("Not your reservation");
        }
        if (r.getStatus() == ReservationStatus.CANCELLED || r.getStatus() == ReservationStatus.REFUSED
                || r.getStatus() == ReservationStatus.ESCALATED) {
            throw new BusinessException("Reservation is already in a terminal state");
        }
        if (r.getStatus() == ReservationStatus.CONFIRMED) {
            throw new BusinessException("Cannot cancel a confirmed reservation — contact support");
        }
        r.setStatus(ReservationStatus.CANCELLED);
        r.setCancelledAt(LocalDateTime.now());
        reservationRepository.save(r);
        log.info("Reservation cancelled: id={}", reservationId);
        releaseTemporaryReservation(r);
        publishEvent("reservation.cancelled", reservationId, buildEventMap(r, "CANCELLED"));
    }

    // ──────────────────────────────────────────────────────────
    // Get by ID (passenger or driver)
    // ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ReservationResponse getById(String userId, String reservationId) {
        Reservation r = findOrThrow(reservationId);
        if (!r.getPassengerId().equals(userId) && !r.getDriverId().equals(userId)) {
            throw new BusinessException("Not your reservation");
        }
        return toResponse(r);
    }

    // ──────────────────────────────────────────────────────────
    // Internal check for avis-service
    // ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public boolean hasConfirmedReservation(String passengerId, String driverId, String trajetId) {
        return reservationRepository.existsConfirmedReservation(passengerId, driverId, trajetId);
    }

    // ──────────────────────────────────────────────────────────
    // Passenger history & driver pending
    // ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ReservationResponse> getPassengerHistory(String passengerId, int page, int size) {
        int safeSize = Math.clamp(size, 1, 100);
        return reservationRepository.findByPassengerId(passengerId,
                        org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getDriverPending(String driverId, int page, int size) {
        int safeSize = Math.clamp(size, 1, 100);
        return reservationRepository.findByDriverIdAndStatus(
                        driverId, ReservationStatus.PENDING_DRIVER,
                        org.springframework.data.domain.PageRequest.of(Math.max(page, 0), safeSize))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ReservationPricingResponse getPricingForPassenger(String reservationId, String passengerId) {
        Reservation r = findOrThrow(reservationId);
        if (!r.getPassengerId().equals(passengerId)) {
            throw new BusinessException("Not your reservation");
        }
        if (r.getStatus() != ReservationStatus.ACCEPTED && r.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new BusinessException("Reservation is not eligible for payment (status: " + r.getStatus() + ")");
        }
        if (r.getTotalPrice() == null || r.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Invalid reservation price");
        }
        return ReservationPricingResponse.builder()
                .reservationId(r.getId())
                .passengerId(r.getPassengerId())
                .status(r.getStatus().name())
                .totalPrice(r.getTotalPrice())
                .build();
    }

    // ──────────────────────────────────────────────────────────
    // Admin methods
    // ──────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<ReservationResponse> adminListAll(String status, String passengerId,
                                                  String driverId, Pageable pageable) {
        if (status != null && passengerId == null && driverId == null) {
            try {
                ReservationStatus rs = ReservationStatus.valueOf(status.toUpperCase());
                return reservationRepository.findByStatus(rs, pageable).map(this::toResponse);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid status: " + status);
            }
        }
        if (passengerId != null && status == null && driverId == null) {
            return reservationRepository.findByPassengerId(passengerId, pageable).map(this::toResponse);
        }
        Specification<Reservation> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) {
                try { predicates.add(cb.equal(root.get("status"), ReservationStatus.valueOf(status.toUpperCase()))); }
                catch (IllegalArgumentException e) { throw new BusinessException("Invalid status: " + status); }
            }
            if (passengerId != null) predicates.add(cb.equal(root.get("passengerId"), passengerId));
            if (driverId    != null) predicates.add(cb.equal(root.get("driverId"),    driverId));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return reservationRepository.findAll(spec, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ReservationResponse adminGetById(String id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public void adminForceCancel(String adminId, String id, String reason) {
        Reservation r = findOrThrow(id);
        if (r.getStatus() == ReservationStatus.CONFIRMED) {
            throw new BusinessException("Cannot force cancel a confirmed reservation without refund — use refund-and-cancel");
        }
        r.setStatus(ReservationStatus.CANCELLED);
        r.setRefusalReason(reason);
        r.setCancelledAt(LocalDateTime.now());
        reservationRepository.save(r);
        releaseTemporaryReservation(r);
        publishEvent("reservation.cancelled", id, buildEventMap(r, "CANCELLED"));
        log.info("Reservation {} force cancelled by admin {}", id, adminId);
    }

    @Transactional
    public void adminForceConfirm(String adminId, String id) {
        Reservation r = findOrThrow(id);
        if (r.getStatus() != ReservationStatus.ACCEPTED && r.getStatus() != ReservationStatus.PAYMENT_PENDING) {
            throw new BusinessException("Reservation cannot be force confirmed (status: " + r.getStatus() + ")");
        }
        confirmSeats(r);
        r.setStatus(ReservationStatus.CONFIRMED);
        r.setConfirmedAt(LocalDateTime.now());
        r.setPaidAt(LocalDateTime.now());
        reservationRepository.save(r);
        publishEvent("reservation.confirmed", id, buildEventMap(r, "CONFIRMED"));
        log.info("Reservation {} force confirmed by admin {} (seats confirmed on trajet)", id, adminId);
    }

    @Transactional
    public void adminForceEscalate(String adminId, String id) {
        Reservation r = findOrThrow(id);
        if (r.getStatus() != ReservationStatus.PENDING_DRIVER) {
            throw new BusinessException("Only pending reservations can be manually escalated");
        }
        r.setStatus(ReservationStatus.ESCALATED);
        r.setEscalated(true);
        r.setCancelledAt(LocalDateTime.now());
        reservationRepository.save(r);
        releaseTemporaryReservation(r);
        publishEvent("reservation.escalated", id, buildEventMap(r, "ESCALATED"));
        log.info("Reservation {} force escalated by admin {}", id, adminId);
    }

    @Transactional
    public void refundAndCancel(String adminId, String reservationId, String reason) {
        Reservation r = findOrThrow(reservationId);
        if (r.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException("Only confirmed reservations can be refunded and cancelled");
        }
        if (r.getPaymentIntentId() == null || r.getPaymentIntentId().isBlank()) {
            throw new BusinessException("No payment information found for this reservation");
        }
        try {
            paymentServiceWebClient.post()
                    .uri("/api/v1/payments/refund/{paymentId}", r.getPaymentIntentId())
                    .header("X-User-Id", adminId)
                    .header("X-Internal-Secret", internalApiKey)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofSeconds(5));
            log.info("Payment refunded for reservation {}", reservationId);
        } catch (Exception e) {
            log.error("Failed to refund payment for reservation {}: {}", reservationId, e.getMessage());
            throw new BusinessException("Refund failed: " + e.getMessage());
        }
        r.setStatus(ReservationStatus.CANCELLED);
        r.setRefusalReason(reason != null ? reason : "Cancelled by admin with refund");
        r.setCancelledAt(LocalDateTime.now());
        reservationRepository.save(r);
        restoreSeatsOnTrajet(r);
        publishEvent("reservation.cancelled", reservationId, buildEventMap(r, "CANCELLED"));
        log.info("Reservation {} refunded and cancelled by admin {}", reservationId, adminId);
    }

    @Transactional
    public void adminUpdateStatus(String reservationId, String targetStatus, String reason) {
        Reservation r = findOrThrow(reservationId);
        ReservationStatus newStatus = ReservationStatus.valueOf(targetStatus.toUpperCase());
        switch (newStatus) {
            case CANCELLED -> {
                if (r.getStatus() == ReservationStatus.CONFIRMED) {
                    throw new BusinessException("Use refund-and-cancel for confirmed reservations");
                }
                adminForceCancel("SYSTEM", reservationId, reason);
            }
            case CONFIRMED -> adminForceConfirm("SYSTEM", reservationId);
            case ESCALATED -> adminForceEscalate("SYSTEM", reservationId);
            default -> throw new BusinessException("Unsupported admin status transition: " + targetStatus);
        }
    }

    // ──────────────────────────────────────────────────────────
    // Schedulers (ShedLock)
    // ──────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "reservation-sendDriverReminders", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void sendDriverReminders() {
        LocalDateTime fiveMinAgo = LocalDateTime.now().minusMinutes(reminderMinutes);
        List<Reservation> pending = reservationRepository.findPendingForReminder(fiveMinAgo);
        for (Reservation r : pending) {
            reservationSchedulerService.sendReminderForOne(r.getId());
        }
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "reservation-notifyAdminPending", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void notifyAdminForPendingReservations() {
        LocalDateTime eightMinAgo = LocalDateTime.now().minusMinutes(adminNotificationMinutes);
        List<Reservation> pending = reservationRepository.findPendingForAdminNotification(eightMinAgo);
        for (Reservation r : pending) {
            reservationSchedulerService.notifyAdminForOne(r.getId());
        }
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "reservation-escalateExpiredReservations", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void escalateExpiredReservations() {
        List<Reservation> expired = reservationRepository.findExpiredPending(LocalDateTime.now());
        for (Reservation r : expired) {
            reservationSchedulerService.escalateOne(r.getId());
        }
    }

    // ─── Dashboard stats ──────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Map<String, Object> getReservationStats() {
        long totalConfirmed = reservationRepository.countByStatus(ReservationStatus.CONFIRMED);
        long totalPending   = reservationRepository.countByStatus(ReservationStatus.PENDING_DRIVER);
        long totalCancelled = reservationRepository.countByStatus(ReservationStatus.CANCELLED);
        long totalEscalated = reservationRepository.countByStatus(ReservationStatus.ESCALATED);
        long totalRefused   = reservationRepository.countByStatus(ReservationStatus.REFUSED);
        LocalDateTime monthStart = LocalDateTime.now()
                .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long newThisMonth = reservationRepository.countByCreatedAtBetween(monthStart, LocalDateTime.now());
        return Map.of(
                "totalConfirmed",  totalConfirmed,
                "totalPending",    totalPending,
                "totalCancelled",  totalCancelled,
                "totalEscalated",  totalEscalated,
                "totalRefused",    totalRefused,
                "newThisMonth",    newThisMonth
        );
    }

    // ──────────────────────────────────────────────────────────
    // Private helpers – seat management (CORRECTED)
    // ──────────────────────────────────────────────────────────
    private void reserveSeatsTemporarily(Reservation r) {
        trajetServiceWebClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/trajets/internal/{id}/reserve-temp")
                        .queryParam("seats", r.getSeatsReserved())
                        .build(Map.of("id", r.getTrajetId())))
                .header("X-Internal-Secret", internalApiKey)
                .retrieve()
                .bodyToMono(Void.class)
                .block(Duration.ofSeconds(5));
    }

    private void confirmSeats(Reservation r) {
        trajetServiceWebClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/trajets/internal/{id}/confirm-seats")
                        .queryParam("seats", r.getSeatsReserved())
                        .build(Map.of("id", r.getTrajetId())))
                .header("X-Internal-Secret", internalApiKey)
                .retrieve()
                .bodyToMono(Void.class)
                .block(Duration.ofSeconds(5));
    }

    private void releaseTemporaryReservation(Reservation r) {
        try {
            trajetServiceWebClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/trajets/internal/{id}/release-temp")
                            .queryParam("seats", r.getSeatsReserved())
                            .build(Map.of("id", r.getTrajetId())))
                    .header("X-Internal-Secret", internalApiKey)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            log.error("Failed to release temporary seats for trajet {}: {}", r.getTrajetId(), e.getMessage());
            throw new BusinessException("Technical error: could not release seats. Please contact support. " + e.getMessage());
        }
    }

    private void restoreSeatsOnTrajet(Reservation r) {
        try {
            trajetServiceWebClient.patch()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/trajets/internal/{id}/increment-seats")
                            .queryParam("seats", r.getSeatsReserved())
                            .build(Map.of("id", r.getTrajetId())))
                    .header("X-Internal-Secret", internalApiKey)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofSeconds(5));
        } catch (Exception e) {
            log.error("Failed to restore seats on trajet {}: {}", r.getTrajetId(), e.getMessage());
            throw new BusinessException("Technical error: could not release seats. Please contact support. " + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────
    // Private helpers – general
    // ──────────────────────────────────────────────────────────
    private Reservation findOrThrow(String id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + id));
    }

    private Reservation findAndVerifyDriver(String driverId, String id) {
        Reservation r = findOrThrow(id);
        if (!r.getDriverId().equals(driverId)) throw new BusinessException("Not your reservation");
        return r;
    }

    private ReservationResponse toResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .trajetId(r.getTrajetId())
                .passengerId(r.getPassengerId())
                .driverId(r.getDriverId())
                .seatsReserved(r.getSeatsReserved())
                .totalPrice(r.getTotalPrice())
                .status(r.getStatus().name())
                .refusalReason(r.getRefusalReason())
                .driverResponseDeadline(r.getDriverResponseDeadline())
                .confirmedAt(r.getConfirmedAt())
                .paidAt(r.getPaidAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private void publishEvent(String eventType, String aggregateId, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(json)
                    .build();
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to create outbox event for {}: {}", eventType, e.getMessage());
        }
    }

    private Map<String, Object> buildEventMap(Reservation r, String type) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", type);
        event.put("reservationId", r.getId());
        event.put("trajetId", r.getTrajetId());
        event.put("passengerId", r.getPassengerId());
        event.put("driverId", r.getDriverId());
        event.put("seatsReserved", r.getSeatsReserved());
        event.put("totalPrice", r.getTotalPrice() != null ? r.getTotalPrice().toString() : "0");
        event.put("departureTime", r.getDepartureDate() != null ? r.getDepartureDate().toString() : "");
        return event;
    }
}