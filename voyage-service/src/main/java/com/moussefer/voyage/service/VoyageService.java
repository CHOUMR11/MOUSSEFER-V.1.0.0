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
        if (reservationRepository.existsByVoyageIdAndPassengerId(voyage.getId(), passengerId)) {
            throw new BusinessException("You already reserved for this voyage");
        }

        // Anti-overbooking : calculer les places réellement disponibles
        // en tenant compte des réservations en cours (PENDING_ORGANIZER + PENDING_PAYMENT + CONFIRMED)
        int pendingAndConfirmedSeats = reservationRepository.sumPendingAndConfirmedSeats(voyage.getId());
        int effectiveAvailable = voyage.getAvailableSeats() - pendingAndConfirmedSeats;
        if (effectiveAvailable < request.getSeats()) {
            throw new BusinessException("Not enough available seats. Effective available: "
                    + effectiveAvailable + " (total: " + voyage.getAvailableSeats()
                    + ", pending/confirmed: " + pendingAndConfirmedSeats + ")");
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

    // FIX #13: was setting CONFIRMED before payment + calling paymentService.initiatePayment()
    // with wrong signature (only reservationId, method needs InitiatePaymentRequest + passengerId + driverId).
    // CORRECT flow: organizer accepts → PENDING_PAYMENT → passenger pays → webhook → CONFIRMED
    @Transactional
    public ReservationVoyageResponse acceptReservation(String organizerId, String reservationId) {
        ReservationVoyage reservation = findReservationAndCheckOrganizer(reservationId, organizerId);
        if (reservation.getStatus() != ReservationVoyageStatus.PENDING_ORGANIZER) {
            throw new BusinessException("Reservation already processed");
        }
        // Transition to PENDING_PAYMENT — passenger must now complete Stripe payment
        // Stripe webhook will call confirmVoyagePayment() which transitions to CONFIRMED
        reservation.setStatus(ReservationVoyageStatus.PENDING_PAYMENT);
        reservation.setConfirmedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        // Notify passenger that they can now proceed with payment
        eventProducer.sendReservationConfirmed(reservation);
        log.info("Voyage reservation {} accepted by organizer {} — awaiting payment", reservationId, organizerId);
        return ReservationVoyageResponse.from(reservation);
    }

    /**
     * Called by StripeWebhookService after voyage payment confirmation.
     * Transitions PENDING_PAYMENT → CONFIRMED and fires voyage.payment.confirmed Kafka event.
     */
    @Transactional
    public void confirmVoyagePayment(String reservationId, String stripePaymentIntentId) {
        ReservationVoyage reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));
        if (reservation.getStatus() != ReservationVoyageStatus.PENDING_PAYMENT) {
            log.warn("Voyage payment confirmation ignored: reservation {} status={}", reservationId, reservation.getStatus());
            return;
        }

        // Decrement seats on voyage
        Voyage voyage = voyageRepository.findById(reservation.getVoyageId())
                .orElseThrow(() -> new ResourceNotFoundException("Voyage not found"));
        int updated = voyageRepository.decrementSeats(voyage.getId(), reservation.getSeatsReserved());
        if (updated == 0) {
            throw new BusinessException("Failed to confirm seats — concurrent modification");
        }
        voyage = voyageRepository.findById(voyage.getId()).orElseThrow();
        if (voyage.getAvailableSeats() == 0) {
            voyage.setStatus(VoyageStatus.FULL);
            voyageRepository.save(voyage);
        }

        reservation.setStatus(ReservationVoyageStatus.CONFIRMED);
        reservation.setPaidAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        // Kafka: voyage.payment.confirmed → loyalty-service (US-92) + notification-service
        eventProducer.sendVoyagePaymentConfirmed(reservation);
        log.info("Voyage reservation {} confirmed after payment {}", reservationId, stripePaymentIntentId);
    }

    @Transactional
    public void refuseReservation(String organizerId, String reservationId, String reason) {
        ReservationVoyage reservation = findReservationAndCheckOrganizer(reservationId, organizerId);
        if (reservation.getStatus() != ReservationVoyageStatus.PENDING_ORGANIZER) {
            throw new BusinessException("Reservation already processed");
        }
        reservation.setStatus(ReservationVoyageStatus.CANCELLED);
        reservationRepository.save(reservation);

        // NOTE: pas de restoration de places ici — les places ne sont décrémentées
        // qu'au moment du paiement confirmé (confirmVoyagePayment). Restaurer ici
        // gonflerait availableSeats au-delà de totalSeats.

        log.info("Reservation {} refused by organizer {}, reason: {}", reservationId, organizerId, reason);
    }

    // Called by Stripe webhook
    @Transactional
    public void confirmPayment(String paymentIntentId, String reservationId) {
        paymentService.confirmPayment(paymentIntentId, reservationId);
        invoiceService.generateAndStoreInvoice(reservationId);
        log.info("Payment confirmed for reservation {}", reservationId);
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