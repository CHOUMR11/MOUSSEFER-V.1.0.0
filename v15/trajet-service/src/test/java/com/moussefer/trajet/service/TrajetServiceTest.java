package com.moussefer.trajet.service;

import com.moussefer.trajet.dto.request.CreateTrajetRequest;
import com.moussefer.trajet.entity.Trajet;
import com.moussefer.trajet.entity.TrajetStatus;
import com.moussefer.trajet.exception.BusinessException;
import com.moussefer.trajet.exception.ResourceNotFoundException;
import com.moussefer.trajet.kafka.TrajetEventProducer;
import com.moussefer.trajet.repository.TrajetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TrajetService — priority queue and seat management")
class TrajetServiceTest {

    @Mock
    private TrajetRepository trajetRepository;

    @Mock
    private TrajetEventProducer eventProducer;

    @InjectMocks
    private TrajetService trajetService;

    private Trajet activeTrajet;
    private Trajet lockedTrajet;

    @BeforeEach
    void setUp() {
        activeTrajet = Trajet.builder()
                .id("trajet-active-001")
                .driverId("driver-001")
                .departureCity("Tunis")
                .arrivalCity("Sfax")
                .departureDate(LocalDateTime.now().plusDays(1))
                .totalSeats(4)
                .availableSeats(4)
                .status(TrajetStatus.ACTIVE)
                .priorityOrder(1)
                .pricePerSeat(BigDecimal.valueOf(15.0))
                .version(0L)
                .build();

        lockedTrajet = Trajet.builder()
                .id("trajet-locked-002")
                .driverId("driver-002")
                .departureCity("Tunis")
                .arrivalCity("Sfax")
                .departureDate(LocalDateTime.now().plusDays(1))
                .totalSeats(3)
                .availableSeats(3)
                .status(TrajetStatus.LOCKED)
                .priorityOrder(2)
                .pricePerSeat(BigDecimal.valueOf(12.0))
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("First trajet published → ACTIVE, priority 1")
    void firstTrajetGetsActive() {
        when(trajetRepository.lockRouteForDate(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(trajetRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventProducer).sendTrajetPublished(any());

        var response = trajetService.publishTrajet("driver-001", buildCreateRequest());

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getPriorityOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("Second trajet on same route → LOCKED, priority 2")
    void secondTrajetGetsLocked() {
        when(trajetRepository.lockRouteForDate(any(), any(), any(), any()))
                .thenReturn(List.of(activeTrajet));
        when(trajetRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventProducer).sendTrajetPublished(any());

        var response = trajetService.publishTrajet("driver-002", buildCreateRequest());

        assertThat(response.getStatus()).isEqualTo("LOCKED");
        assertThat(response.getPriorityOrder()).isEqualTo(2);
    }

    @Test
    @DisplayName("reduceSeats fails if not the driver")
    void reduceSeatsWrongDriver() {
        when(trajetRepository.findById("trajet-active-001"))
                .thenReturn(Optional.of(activeTrajet));

        assertThatThrownBy(() ->
                trajetService.reduceSeats("wrong-driver", "trajet-active-001", 2)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not your trajet");
    }

    @Test
    @DisplayName("reduceSeats fails on optimistic lock conflict")
    void reduceSeatsOptimisticLock() {
        when(trajetRepository.findById("trajet-active-001"))
                .thenReturn(Optional.of(activeTrajet));
        when(trajetRepository.decrementSeatsWithVersion("trajet-active-001", 2, 0L))
                .thenReturn(0);

        assertThatThrownBy(() ->
                trajetService.reduceSeats("driver-001", "trajet-active-001", 2)
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not enough available seats");
    }

    @Test
    @DisplayName("reduceSeats success updates seats and checks FULL")
    void reduceSeatsSuccess() {
        Trajet afterDecrement = Trajet.builder()
                .id("trajet-active-001").driverId("driver-001")
                .departureCity("Tunis").arrivalCity("Sfax")
                .departureDate(LocalDateTime.now().plusDays(1))
                .availableSeats(2).status(TrajetStatus.ACTIVE).version(1L).build();

        when(trajetRepository.findById("trajet-active-001"))
                .thenReturn(Optional.of(activeTrajet))
                .thenReturn(Optional.of(afterDecrement));
        when(trajetRepository.decrementSeatsWithVersion("trajet-active-001", 2, 0L))
                .thenReturn(1);

        var response = trajetService.reduceSeats("driver-001", "trajet-active-001", 2);

        assertThat(response).isNotNull();
        verify(trajetRepository).decrementSeatsWithVersion("trajet-active-001", 2, 0L);
    }

    @Test
    @DisplayName("markDeparted fails if not the driver")
    void markDepartedWrongDriver() {
        when(trajetRepository.findById("trajet-active-001"))
                .thenReturn(Optional.of(activeTrajet));

        assertThatThrownBy(() ->
                trajetService.markDeparted("wrong-driver", "trajet-active-001")
        ).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Not your trajet");
    }

    @Test
    @DisplayName("markDeparted success → status DEPARTED, promotes next")
    void markDepartedSuccess() {
        when(trajetRepository.findById("trajet-active-001"))
                .thenReturn(Optional.of(activeTrajet));
        when(trajetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(trajetRepository.findNextInQueue(any(), any(), any(), any(), any()))
                .thenReturn(List.of(lockedTrajet));
        doNothing().when(eventProducer).sendTrajetDeparted(any());
        doNothing().when(eventProducer).sendTrajetActivated(any());

        trajetService.markDeparted("driver-001", "trajet-active-001");

        verify(trajetRepository).save(argThat(t ->
                t.getId().equals("trajet-active-001") &&
                        t.getStatus() == TrajetStatus.DEPARTED
        ));
        verify(trajetRepository).save(argThat(t ->
                t.getId().equals("trajet-locked-002") &&
                        t.getStatus() == TrajetStatus.ACTIVE
        ));
        verify(eventProducer).sendTrajetDeparted(any());
        verify(eventProducer).sendTrajetActivated(lockedTrajet);
    }

    @Test
    @DisplayName("Find by id not found throws ResourceNotFoundException")
    void findByIdNotFound() {
        when(trajetRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                trajetService.markDeparted("driver-001", "unknown")
        ).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("activateNextTrajetForRoute promotes LOCKED to ACTIVE")
    void activateNextPromotes() {
        when(trajetRepository.findById("trajet-active-001"))
                .thenReturn(Optional.of(activeTrajet));
        when(trajetRepository.findNextInQueue(any(), any(), any(), any(), any()))
                .thenReturn(List.of(lockedTrajet));
        when(trajetRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(eventProducer).sendTrajetActivated(any());

        trajetService.activateNextTrajetForRoute("trajet-active-001");

        verify(trajetRepository).save(argThat(t ->
                t.getId().equals("trajet-locked-002") &&
                        t.getStatus() == TrajetStatus.ACTIVE
        ));
        verify(eventProducer).sendTrajetActivated(lockedTrajet);
    }

    private CreateTrajetRequest buildCreateRequest() {
        var req = new CreateTrajetRequest();
        req.setDepartureCity("Tunis");
        req.setArrivalCity("Sfax");
        req.setDepartureDate(LocalDateTime.now().plusDays(1));
        req.setTotalSeats(4);
        req.setPricePerSeat(new BigDecimal("15.00"));
        return req;
    }
}