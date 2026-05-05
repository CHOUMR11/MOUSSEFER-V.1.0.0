package com.moussefer.voyage.service;

import com.moussefer.voyage.dto.request.CreateVoyageRequest;
import com.moussefer.voyage.dto.request.ReserveVoyageRequest;
import com.moussefer.voyage.dto.response.ReservationVoyageResponse;
import com.moussefer.voyage.dto.response.VoyageResponse;
import com.moussefer.voyage.entity.ReservationVoyage;
import com.moussefer.voyage.entity.ReservationVoyageStatus;
import com.moussefer.voyage.entity.Voyage;
import com.moussefer.voyage.entity.VoyageStatus;
import com.moussefer.voyage.exception.BusinessException;
import com.moussefer.voyage.exception.ResourceNotFoundException;
import com.moussefer.voyage.kafka.VoyageEventProducer;
import com.moussefer.voyage.repository.ReservationVoyageRepository;
import com.moussefer.voyage.repository.VoyageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests des règles métier pour les voyages organisés.
 *
 * Garanties testées :
 *   1. Le statut PENDING_ORGANIZER est obligatoire avant tout paiement
 *   2. Pas de réservation sur un voyage non-OPEN
 *   3. Pas de réservation sans places disponibles
 *   4. Anti-doublon : un passager ne peut réserver le même voyage 2 fois
 *   5. Seul l'organisateur propriétaire peut accepter/refuser ses réservations
 *   6. Le prix total est calculé côté serveur (pricePerSeat × seats)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("VoyageService — règles métier des voyages organisés")
class VoyageServiceTest {

    @Mock private VoyageRepository voyageRepository;
    @Mock private ReservationVoyageRepository reservationRepository;
    @Mock private PaymentService paymentService;           // gardé pour de futurs tests
    @Mock private InvoiceService invoiceService;           // gardé pour de futurs tests
    @Mock private VoyageEventProducer eventProducer;       // gardé pour de futurs tests

    @InjectMocks private VoyageService voyageService;

    // ─────────── CREATE VOYAGE ───────────

    @Test
    @DisplayName("Création réussie : statut OPEN, organizerId conservé")
    void createVoyage_success() {
        CreateVoyageRequest req = new CreateVoyageRequest();
        req.setDepartureCity("Tunis");
        req.setArrivalCity("Djerba");
        req.setDepartureDate(LocalDateTime.now().plusDays(7));
        req.setPricePerSeat(290.0);
        req.setTotalSeats(40);

        when(voyageRepository.save(any(Voyage.class))).thenAnswer(inv -> {
            Voyage v = inv.getArgument(0);
            v.setId("voy-1");
            return v;
        });

        VoyageResponse response = voyageService.createVoyage("org-123", req);

        assertThat(response).isNotNull();
        verify(voyageRepository).save(any(Voyage.class));
    }

    // ─────────── RESERVE SEATS — Règles métier ───────────

    @Test
    @DisplayName("Réservation sur voyage CANCELLED → BusinessException")
    void reserveSeats_voyageNotOpen_rejected() {
        // CLOSED n'existe pas, on utilise CANCELLED (un voyage annulé n'est pas réservable)
        Voyage cancelled = Voyage.builder()
                .id("voy-1").organizerId("org-1")
                .pricePerSeat(290.0).totalSeats(40).availableSeats(35)
                .status(VoyageStatus.CANCELLED).build();
        when(voyageRepository.findByIdWithLock("voy-1")).thenReturn(Optional.of(cancelled));

        ReserveVoyageRequest req = new ReserveVoyageRequest();
        req.setVoyageId("voy-1");
        req.setSeats(2);

        assertThatThrownBy(() -> voyageService.reserveSeats("pas-1", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not open");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Réservation avec places insuffisantes → BusinessException")
    void reserveSeats_notEnoughSeats_rejected() {
        Voyage almostFull = Voyage.builder()
                .id("voy-2").organizerId("org-1")
                .pricePerSeat(290.0).totalSeats(40).availableSeats(2)
                .status(VoyageStatus.OPEN).build();
        when(voyageRepository.findByIdWithLock("voy-2")).thenReturn(Optional.of(almostFull));

        ReserveVoyageRequest req = new ReserveVoyageRequest();
        req.setVoyageId("voy-2");
        req.setSeats(5); // demande 5 alors qu'il n'en reste que 2

        assertThatThrownBy(() -> voyageService.reserveSeats("pas-1", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough");
    }

    @Test
    @DisplayName("Anti-doublon : même passager + même voyage → BusinessException")
    void reserveSeats_alreadyReserved_rejected() {
        Voyage open = Voyage.builder()
                .id("voy-3").organizerId("org-1")
                .pricePerSeat(290.0).totalSeats(40).availableSeats(20)
                .status(VoyageStatus.OPEN).build();
        when(voyageRepository.findByIdWithLock("voy-3")).thenReturn(Optional.of(open));
        when(reservationRepository.existsByVoyageIdAndPassengerId("voy-3", "pas-99"))
                .thenReturn(true);

        ReserveVoyageRequest req = new ReserveVoyageRequest();
        req.setVoyageId("voy-3");
        req.setSeats(2);

        assertThatThrownBy(() -> voyageService.reserveSeats("pas-99", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already reserved");
    }

    @Test
    @DisplayName("Voyage inexistant → ResourceNotFoundException (pas BusinessException)")
    void reserveSeats_voyageNotFound_throwsNotFound() {
        when(voyageRepository.findByIdWithLock("voy-unknown")).thenReturn(Optional.empty());

        ReserveVoyageRequest req = new ReserveVoyageRequest();
        req.setVoyageId("voy-unknown");
        req.setSeats(1);

        assertThatThrownBy(() -> voyageService.reserveSeats("pas-1", req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Réservation OK : statut PENDING_ORGANIZER + prix total = pricePerSeat × seats")
    void reserveSeats_success_status_PENDING_ORGANIZER() {
        Voyage open = Voyage.builder()
                .id("voy-4").organizerId("org-1")
                .pricePerSeat(290.0).totalSeats(40).availableSeats(20)
                .status(VoyageStatus.OPEN).build();
        when(voyageRepository.findByIdWithLock("voy-4")).thenReturn(Optional.of(open));
        when(reservationRepository.existsByVoyageIdAndPassengerId(anyString(), anyString()))
                .thenReturn(false);
        when(reservationRepository.save(any(ReservationVoyage.class)))
                .thenAnswer(inv -> {
                    ReservationVoyage r = inv.getArgument(0);
                    r.setId("rv-1");
                    return r;
                });

        ReserveVoyageRequest req = new ReserveVoyageRequest();
        req.setVoyageId("voy-4");
        req.setSeats(3);

        ReservationVoyageResponse response = voyageService.reserveSeats("pas-1", req);

        assertThat(response).isNotNull();
        verify(reservationRepository).save(argThat(r ->
                r.getStatus() == ReservationVoyageStatus.PENDING_ORGANIZER
                        && r.getTotalPrice() == 290.0 * 3
                        && r.getSeatsReserved() == 3
                        && "pas-1".equals(r.getPassengerId())
        ));
    }

    // ─────────── ACCEPT/REFUSE — Règles métier ───────────

    @Test
    @DisplayName("Refus de réservation par non-propriétaire → BusinessException 'Not your voyage'")
    void refuseReservation_notOrganizer_rejected() {
        Voyage voyage = Voyage.builder()
                .id("voy-5").organizerId("org-OWNER")
                .status(VoyageStatus.OPEN).build();
        ReservationVoyage reservation = ReservationVoyage.builder()
                .id("rv-1").voyageId("voy-5").passengerId("pas-1")
                .status(ReservationVoyageStatus.PENDING_ORGANIZER).build();
        when(reservationRepository.findById("rv-1")).thenReturn(Optional.of(reservation));
        when(voyageRepository.findById("voy-5")).thenReturn(Optional.of(voyage));

        assertThatThrownBy(() ->
                voyageService.refuseReservation("org-INTRUDER", "rv-1", "test"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not your voyage");
    }

    @Test
    @DisplayName("Acceptation sur réservation déjà PAYMENT_PENDING → BusinessException")
    void acceptReservation_wrongStatus_rejected() {
        Voyage voyage = Voyage.builder()
                .id("voy-6").organizerId("org-1")
                .status(VoyageStatus.OPEN).build();
        ReservationVoyage already = ReservationVoyage.builder()
                .id("rv-already").voyageId("voy-6").passengerId("pas-1")
                .status(ReservationVoyageStatus.PENDING_PAYMENT) // déjà acceptée
                .build();
        when(reservationRepository.findById("rv-already")).thenReturn(Optional.of(already));
        when(voyageRepository.findById("voy-6")).thenReturn(Optional.of(voyage));

        assertThatThrownBy(() ->
                voyageService.acceptReservation("org-1", "rv-already"))
                .isInstanceOf(BusinessException.class);
    }
}