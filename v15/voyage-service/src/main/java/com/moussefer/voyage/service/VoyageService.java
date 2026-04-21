package com.moussefer.voyage.service;

import com.moussefer.voyage.dto.request.CreateVoyageRequest;
import com.moussefer.voyage.dto.request.ReserveVoyageRequest;
import com.moussefer.voyage.dto.request.UpdateVoyageRequest;
import com.moussefer.voyage.dto.response.ReservationVoyageResponse;
import com.moussefer.voyage.dto.response.VoyageResponse;
import com.moussefer.voyage.entity.*;
import com.moussefer.voyage.exception.BusinessException;
import com.moussefer.voyage.exception.ResourceNotFoundException;
import com.moussefer.voyage.kafka.VoyageEventProducer;
import com.moussefer.voyage.repository.ReservationVoyageRepository;
import com.moussefer.voyage.repository.VoyageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoyageService {

    private final VoyageRepository voyageRepository;
    private final ReservationVoyageRepository reservationRepository;
    private final PaymentService paymentService;
    private final InvoiceService invoiceService;
    private final VoyageEventProducer eventProducer;

    @org.springframework.beans.factory.annotation.Value("${stripe.webhook-secret:}")
    private String voyageWebhookSecret;

    // ─── Helper methods ─────────────────────────────────────────────────
    private Voyage findVoyageAndCheckOrganizer(String voyageId, String organizerId) {
        Voyage voyage = voyageRepository.findById(voyageId)
                .orElseThrow(() -> new ResourceNotFoundException("Voyage not found"));
        if (!voyage.getOrganizerId().equals(organizerId)) {
            throw new BusinessException("Not your voyage");
        }
        return voyage;
    }

    private void checkOrganizerOwnsVoyage(String voyageId, String organizerId) {
        findVoyageAndCheckOrganizer(voyageId, organizerId);
    }

    private ReservationVoyage findReservationAndCheckOrganizer(String reservationId, String organizerId) {
        ReservationVoyage reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        checkOrganizerOwnsVoyage(reservation.getVoyageId(), organizerId);
        return reservation;
    }

    // ─── Public listing endpoint ────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<VoyageResponse> findAllAvailable(Pageable pageable) {
        return voyageRepository.findByStatusOrderByDepartureDateAsc(VoyageStatus.OPEN, pageable)
                .map(VoyageResponse::from);
    }

    // ─── Organizer actions ──────────────────────────────────────────────
    @Transactional
    public VoyageResponse createVoyage(String organizerId, CreateVoyageRequest request) {
        Voyage voyage = Voyage.builder()
                .organizerId(organizerId)
                .departureCity(request.getDepartureCity())
                .arrivalCity(request.getArrivalCity())
                .departureDate(request.getDepartureDate())
                .totalSeats(request.getTotalSeats())
                .availableSeats(request.getTotalSeats())
                .pricePerSeat(request.getPricePerSeat())
                .status(VoyageStatus.OPEN)
                .build();
        voyage = voyageRepository.save(voyage);
        log.info("Voyage created: id={} by organizer={}", voyage.getId(), organizerId);
        return VoyageResponse.from(voyage);
    }

    @Transactional
    public VoyageResponse updateVoyage(String organizerId, String voyageId, UpdateVoyageRequest request) {
        Voyage voyage = findVoyageAndCheckOrganizer(voyageId, organizerId);
        if (voyage.getStatus() != VoyageStatus.OPEN) {
            throw new BusinessException("Only OPEN voyages can be updated");
        }

        if (request.getDepartureCity() != null) voyage.setDepartureCity(request.getDepartureCity());
        if (request.getArrivalCity() != null) voyage.setArrivalCity(request.getArrivalCity());
        if (request.getDepartureDate() != null) voyage.setDepartureDate(request.getDepartureDate());
        if (request.getPricePerSeat() != null) voyage.setPricePerSeat(request.getPricePerSeat());
        if (request.getTotalSeats() != null) {
            int reserved = voyage.getTotalSeats() - voyage.getAvailableSeats();
            if (request.getTotalSeats() < reserved) {
                throw new BusinessException("Cannot reduce total seats below already reserved seats (" + reserved + ")");
            }
            voyage.setAvailableSeats(request.getTotalSeats() - reserved);
            voyage.setTotalSeats(request.getTotalSeats());
        }

        voyage = voyageRepository.save(voyage);
        log.info("Voyage updated: id={} by organizer={}", voyageId, organizerId);
        return VoyageResponse.from(voyage);
    }

    @Transactional(readOnly = true)
    public Page<VoyageResponse> getMyVoyages(String organizerId, Pageable pageable) {
        return voyageRepository.findByOrganizerId(organizerId, pageable)
                .map(VoyageResponse::from);
    }

    @Transactional
    public void organizerCancelVoyage(String organizerId, String voyageId) {
        Voyage voyage = findVoyageAndCheckOrganizer(voyageId, organizerId);
        if (voyage.getStatus() != VoyageStatus.OPEN && voyage.getStatus() != VoyageStatus.FULL) {
            throw new BusinessException("Cannot cancel voyage with status: " + voyage.getStatus());
        }
        voyage.setStatus(VoyageStatus.CANCELLED);
        voyageRepository.save(voyage);

        List<ReservationVoyage> reservations = reservationRepository.findByVoyageId(voyageId);
        for (ReservationVoyage r : reservations) {
            if (r.getStatus() != ReservationVoyageStatus.CANCELLED) {
                if (r.getStatus() == ReservationVoyageStatus.CONFIRMED && r.getPaidAt() != null) {
                    paymentService.refundReservation(r);
                    eventProducer.sendVoyageCancelledRefund(r, voyageId);
                }
                r.setStatus(ReservationVoyageStatus.CANCELLED);
                reservationRepository.save(r);
            }
        }
        log.info("Voyage {} cancelled by organizer {}", voyageId, organizerId);
    }

    // ─── Passenger actions ──────────────────────────────────────────────
    @Transactional
    public ReservationVoyageResponse reserveSeats(String passengerId, ReserveVoyageRequest request) {
        Voyage voyage = voyageRepository.findByIdWithLock(request.getVoyageId())
                .orElseThrow(() -> new ResourceNotFoundException("Voyage not found"));

        if (voyage.getStatus() != VoyageStatus.OPEN) {
            throw new BusinessException("Voyage is not open for reservations");
        }
        if (voyage.getAvailableSeats() < request.getSeats()) {
            throw new BusinessException("Not enough available seats");
        }
        if (reservationRepository.existsByVoyageIdAndPassengerId(voyage.getId(), passengerId)) {
            throw new BusinessException("You already reserved for this voyage");
        }

        double totalPrice = voyage.getPricePerSeat() * request.getSeats();
        ReservationVoyage reservation = ReservationVoyage.builder()
                .voyageId(voyage.getId())
                .passengerId(passengerId)
                .seatsReserved(request.getSeats())
                .totalPrice(totalPrice)
                .status(ReservationVoyageStatus.PENDING_ORGANIZER)
                .build();
        reservation = reservationRepository.save(reservation);
        log.info("Reservation created for voyage {} by passenger {}, pending organizer approval", voyage.getId(), passengerId);
        return ReservationVoyageResponse.from(reservation);
    }

    // ✅ CORRECTED acceptReservation – does NOT confirm before payment
    @Transactional
    public ReservationVoyageResponse acceptReservation(String organizerId, String reservationId) {
        ReservationVoyage reservation = findReservationAndCheckOrganizer(reservationId, organizerId);
        if (reservation.getStatus() != ReservationVoyageStatus.PENDING_ORGANIZER) {
            throw new BusinessException("Reservation already processed");
        }
        // 1. Initiate Stripe PaymentIntent (assume method exists)
        paymentService.initiatePayment(reservationId);
        // 2. Change status to PENDING_PAYMENT (not CONFIRMED)
        reservation.setStatus(ReservationVoyageStatus.PENDING_PAYMENT);
        reservation.setConfirmedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        // 3. Do NOT send reservation.confirmed yet – wait for webhook
        // eventProducer.sendReservationConfirmed(reservation); // removed
        log.info("Reservation {} accepted by organizer {}, pending payment", reservationId, organizerId);
        return ReservationVoyageResponse.from(reservation);
    }

    @Transactional
    public void refuseReservation(String organizerId, String reservationId, String reason) {
        ReservationVoyage reservation = findReservationAndCheckOrganizer(reservationId, organizerId);
        if (reservation.getStatus() != ReservationVoyageStatus.PENDING_ORGANIZER) {
            throw new BusinessException("Reservation already processed");
        }
        reservation.setStatus(ReservationVoyageStatus.CANCELLED);
        reservationRepository.save(reservation);

        // Restore seats
        Voyage voyage = voyageRepository.findById(reservation.getVoyageId())
                .orElseThrow(() -> new ResourceNotFoundException("Voyage not found"));
        voyage.setAvailableSeats(voyage.getAvailableSeats() + reservation.getSeatsReserved());
        voyageRepository.save(voyage);

        log.info("Reservation {} refused by organizer {}, reason: {}", reservationId, organizerId, reason);
    }

    // Called by Stripe webhook – now confirms the reservation
    @Transactional
    public void confirmPayment(String paymentIntentId, String reservationId) {
        paymentService.confirmPayment(paymentIntentId, reservationId);
        invoiceService.generateAndStoreInvoice(reservationId);

        // After payment confirmation, update status to CONFIRMED and send event
        ReservationVoyage reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        reservation.setStatus(ReservationVoyageStatus.CONFIRMED);
        reservation.setPaidAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        // Now safe to send the confirmed event
        eventProducer.sendReservationConfirmed(reservation);

        log.info("Payment confirmed and reservation {} set to CONFIRMED", reservationId);
    }

    /**
     * Handle Stripe webhook for voyage payments.
     */
    public void handleStripeWebhook(String payload, String sigHeader) {
        com.stripe.model.Event event;
        try {
            event = com.stripe.net.Webhook.constructEvent(payload, sigHeader, voyageWebhookSecret);
        } catch (Exception e) {
            log.error("Invalid Stripe webhook signature for voyage payment: {}", e.getMessage());
            throw new RuntimeException("Invalid signature");
        }

        if (!"payment_intent.succeeded".equals(event.getType())) {
            log.debug("Ignoring voyage webhook event type: {}", event.getType());
            return;
        }

        com.stripe.model.PaymentIntent intent = (com.stripe.model.PaymentIntent) event
                .getDataObjectDeserializer().getObject().orElse(null);
        if (intent == null) {
            log.warn("Voyage webhook: cannot deserialize PaymentIntent");
            return;
        }

        String reservationId = intent.getMetadata() != null ? intent.getMetadata().get("voyageReservationId") : null;
        if (reservationId == null || reservationId.isBlank()) {
            log.debug("Voyage webhook: no voyageReservationId metadata, likely a trajet payment, skipping");
            return;
        }

        confirmPayment(intent.getId(), reservationId);
        log.info("Voyage webhook processed: reservationId={}, intentId={}", reservationId, intent.getId());
    }

    // ─── Search and read ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public Page<VoyageResponse> searchVoyages(String departureCity, String arrivalCity, LocalDate date,
                                              String organizerId, Double minPrice, Double maxPrice, Pageable pageable) {
        LocalDateTime from = date != null ? date.atStartOfDay() : LocalDateTime.now();
        LocalDateTime to = date != null ? from.plusDays(1) : from.plusMonths(1);
        String orgId = (organizerId != null && !organizerId.isBlank()) ? organizerId : null;
        Page<Voyage> voyages = voyageRepository.searchVoyages(
                departureCity, arrivalCity, from, to, VoyageStatus.OPEN, orgId, minPrice, maxPrice, pageable);
        return voyages.map(VoyageResponse::from);
    }

    @Transactional(readOnly = true)
    public VoyageResponse getVoyage(String id) {
        Voyage voyage = voyageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Voyage not found"));
        return VoyageResponse.from(voyage);
    }

    @Transactional(readOnly = true)
    public Page<ReservationVoyageResponse> getMyReservations(String passengerId, Pageable pageable) {
        return reservationRepository.findByPassengerId(passengerId, pageable)
                .map(ReservationVoyageResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ReservationVoyageResponse> getReservationsForOrganizer(String organizerId, String voyageId) {
        checkOrganizerOwnsVoyage(voyageId, organizerId);
        return reservationRepository.findByVoyageId(voyageId).stream()
                .map(ReservationVoyageResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<ReservationVoyageResponse> getReservationsForOrganizer(String organizerId, String voyageId, Pageable pageable) {
        checkOrganizerOwnsVoyage(voyageId, organizerId);
        return reservationRepository.findByVoyageId(voyageId, pageable)
                .map(ReservationVoyageResponse::from);
    }

    @Transactional(readOnly = true)
    public ReservationVoyageResponse getReservationById(String userId, String reservationId) {
        ReservationVoyage reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
        if (!reservation.getPassengerId().equals(userId)) {
            Voyage voyage = voyageRepository.findById(reservation.getVoyageId())
                    .orElseThrow(() -> new ResourceNotFoundException("Voyage not found"));
            if (!voyage.getOrganizerId().equals(userId)) {
                throw new BusinessException("Not your reservation");
            }
        }
        return ReservationVoyageResponse.from(reservation);
    }

    // ─── Admin actions (called by admin-service via internal endpoint) ──
    @Transactional
    public void adminCancelVoyage(String voyageId) {
        Voyage voyage = voyageRepository.findById(voyageId)
                .orElseThrow(() -> new ResourceNotFoundException("Voyage not found"));
        voyage.setStatus(VoyageStatus.CANCELLED);
        voyageRepository.save(voyage);
        log.info("Voyage {} cancelled by admin", voyageId);
    }
}