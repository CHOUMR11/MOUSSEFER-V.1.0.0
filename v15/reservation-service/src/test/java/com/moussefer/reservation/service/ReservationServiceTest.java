package com.moussefer.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moussefer.reservation.dto.response.ReservationResponse;
import com.moussefer.reservation.entity.Reservation;
import com.moussefer.reservation.entity.ReservationStatus;
import com.moussefer.reservation.exception.BusinessException;
import com.moussefer.reservation.exception.ResourceNotFoundException;
import com.moussefer.reservation.repository.OutboxEventRepository;
import com.moussefer.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService — workflow de réservation")
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ReservationSchedulerService reservationSchedulerService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReservationService reservationService;

    private Reservation pendingReservation;
    private Reservation acceptedReservation;
    private Reservation confirmedReservation;

    @BeforeEach
    void setUp() {
        pendingReservation = Reservation.builder()
                .id("resa-001")
                .trajetId("trajet-001")
                .passengerId("passenger-001")
                .driverId("driver-001")
                .seatsReserved(2)
                .totalPrice(new BigDecimal("40.00"))
                .status(ReservationStatus.PENDING_DRIVER)
                .driverResponseDeadline(LocalDateTime.now().plusMinutes(15))
                .build();

        acceptedReservation = Reservation.builder()
                .id("resa-002")
                .trajetId("trajet-001")
                .passengerId("passenger-001")
                .driverId("driver-001")
                .seatsReserved(1)
                .totalPrice(new BigDecimal("20.00"))
                .status(ReservationStatus.ACCEPTED)
                .driverResponseDeadline(LocalDateTime.now().plusMinutes(10))
                .build();

        confirmedReservation = Reservation.builder()
                .id("resa-003")
                .trajetId("trajet-001")
                .passengerId("passenger-001")
                .driverId("driver-001")
                .seatsReserved(1)
                .totalPrice(new BigDecimal("20.00"))
                .status(ReservationStatus.CONFIRMED)
                .build();
    }

    // ── ACCEPTATION CHAUFFEUR ─────────────────────────────────────────────────

    @Test
    @DisplayName("Chauffeur accepte une réservation PENDING_DRIVER → ACCEPTED")
    void driverAccept_transitions_to_accepted() throws Exception {
        when(reservationRepository.findById("resa-001")).thenReturn(Optional.of(pendingReservation));
        when(reservationRepository.save(any())).thenReturn(pendingReservation);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenReturn(null);

        ReservationResponse response = reservationService.driverAccept("driver-001", "resa-001");

        assertThat(response.getStatus()).isEqualTo("ACCEPTED");
        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == ReservationStatus.ACCEPTED && r.getConfirmedAt() != null
        ));
    }

    @Test
    @DisplayName("Chauffeur ne peut pas accepter une réservation qui n'est pas la sienne")
    void driverAccept_fails_wrong_driver() {
        when(reservationRepository.findById("resa-001")).thenReturn(Optional.of(pendingReservation));

        assertThatThrownBy(() -> reservationService.driverAccept("other-driver", "resa-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not your reservation");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chauffeur ne peut pas accepter une réservation déjà ACCEPTED")
    void driverAccept_fails_wrong_status() {
        when(reservationRepository.findById("resa-002")).thenReturn(Optional.of(acceptedReservation));

        assertThatThrownBy(() -> reservationService.driverAccept("driver-001", "resa-002"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot accept");
    }

    // ── REFUS CHAUFFEUR ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Chauffeur refuse avec motif → statut REFUSED, motif persisté")
    void driverRefuse_sets_refused_status_and_reason() throws Exception {
        when(reservationRepository.findById("resa-001")).thenReturn(Optional.of(pendingReservation));
        when(reservationRepository.save(any())).thenReturn(pendingReservation);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenReturn(null);

        reservationService.driverRefuse("driver-001", "resa-001", "Véhicule complet");

        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == ReservationStatus.REFUSED &&
                        "Véhicule complet".equals(r.getRefusalReason()) &&
                        r.getCancelledAt() != null
        ));
    }

    // ── ANNULATION PASSAGER ───────────────────────────────────────────────────

    @Test
    @DisplayName("Passager annule une réservation non confirmée → CANCELLED")
    void cancel_pending_reservation_succeeds() throws Exception {
        when(reservationRepository.findById("resa-001")).thenReturn(Optional.of(pendingReservation));
        when(reservationRepository.save(any())).thenReturn(pendingReservation);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenReturn(null);

        reservationService.cancel("passenger-001", "resa-001");

        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == ReservationStatus.CANCELLED
        ));
    }

    @Test
    @DisplayName("Passager ne peut pas annuler une réservation CONFIRMED")
    void cancel_confirmed_reservation_throws() {
        when(reservationRepository.findById("resa-003")).thenReturn(Optional.of(confirmedReservation));

        assertThatThrownBy(() -> reservationService.cancel("passenger-001", "resa-003"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot cancel a confirmed reservation");
    }

    @Test
    @DisplayName("Passager ne peut pas annuler la réservation d'un autre")
    void cancel_wrong_passenger_throws() {
        when(reservationRepository.findById("resa-001")).thenReturn(Optional.of(pendingReservation));

        assertThatThrownBy(() -> reservationService.cancel("other-passenger", "resa-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not your reservation");
    }

    // ── CONFIRMATION PAIEMENT ─────────────────────────────────────────────────

    @Test
    @DisplayName("confirmPayment sur réservation ACCEPTED → statut CONFIRMED")
    void confirmPayment_accepted_becomes_confirmed() throws Exception {
        when(reservationRepository.findById("resa-002")).thenReturn(Optional.of(acceptedReservation));
        when(reservationRepository.save(any())).thenReturn(acceptedReservation);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenReturn(null);

        reservationService.confirmPayment("resa-002", "pi_stripe_123");

        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == ReservationStatus.CONFIRMED &&
                        "pi_stripe_123".equals(r.getPaymentIntentId()) &&
                        r.getPaidAt() != null
        ));
    }

    @Test
    @DisplayName("confirmPayment ignoré si réservation déjà CONFIRMED — idempotence")
    void confirmPayment_idempotent_on_confirmed() throws Exception {
        when(reservationRepository.findById("resa-003")).thenReturn(Optional.of(confirmedReservation));

        reservationService.confirmPayment("resa-003", "pi_duplicate");

        verify(reservationRepository, never()).save(any());
    }

    // ── RESSOURCE INEXISTANTE ─────────────────────────────────────────────────

    @Test
    @DisplayName("Réservation inexistante → ResourceNotFoundException")
    void not_found_throws_resource_not_found() {
        when(reservationRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.driverAccept("driver-001", "unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}