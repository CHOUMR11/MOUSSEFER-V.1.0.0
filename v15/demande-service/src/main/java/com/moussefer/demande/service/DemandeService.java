package com.moussefer.demande.service;

import com.moussefer.demande.dto.*;
import com.moussefer.demande.entity.*;
import com.moussefer.demande.exception.BusinessException;
import com.moussefer.demande.exception.ResourceNotFoundException;
import com.moussefer.demande.kafka.DemandeEventPublisher;
import com.moussefer.demande.repository.DemandePassagerRepository;
import com.moussefer.demande.repository.DemandeRepository;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemandeService {

    private final DemandeRepository demandeRepo;
    private final DemandePassagerRepository passagerRepo;
    private final DemandeEventPublisher eventPublisher;
    private final WebClient trajetServiceWebClient;

    @Value("${demande.threshold.default:4}")
    private int defaultThreshold;

    // ==================== CRÉATION ====================
    @Transactional
    public DemandeCollective createDemande(String organisateurId, DemandeCreationRequest request) {
        int capacity = request.getVehicleType().getCapacity();
        Integer seuil = request.getSeuilPersonnalise();
        if (seuil != null && seuil > capacity) {
            throw new BusinessException("Le seuil ne peut pas dépasser la capacité du véhicule (" + capacity + " places)");
        }

        DemandeCollective demande = DemandeCollective.builder()
                .organisateurId(organisateurId)
                .departureCity(request.getDepartureCity())
                .arrivalCity(request.getArrivalCity())
                .requestedDate(request.getRequestedDate())
                .vehicleType(request.getVehicleType())
                .totalCapacity(capacity)
                .seuilPersonnalise(seuil)
                .status(DemandeStatus.OPEN)
                .build();
        demande = demandeRepo.save(demande);
        log.info("Demande collective créée par organisateur {}: id={}, capacité={}", organisateurId, demande.getId(), capacity);
        return demande;
    }

    // ==================== PARTICIPATION ====================
    @Transactional
    public DemandeCollective joinDemande(String passengerId, String demandeId, int seatsReserved) {
        DemandeCollective demande = demandeRepo.findByIdAndStatus(demandeId, DemandeStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Demande ouverte non trouvée: " + demandeId));

        if (passagerRepo.existsByDemandeIdAndPassengerId(demandeId, passengerId)) {
            throw new BusinessException("Vous avez déjà rejoint cette demande");
        }

        int currentReserved = passagerRepo.sumSeatsReservedByDemandeId(demandeId);
        if (currentReserved + seatsReserved > demande.getTotalCapacity()) {
            throw new BusinessException("Capacité maximale atteinte (" + demande.getTotalCapacity() + " places)");
        }

        DemandePassager participation = DemandePassager.builder()
                .demandeId(demandeId)
                .passengerId(passengerId)
                .seatsReserved(seatsReserved)
                .build();
        passagerRepo.save(participation);

        demande.setTotalSeatsReserved(currentReserved + seatsReserved);
        demandeRepo.save(demande);
        log.info("Passager {} a réservé {} place(s) pour la demande {}", passengerId, seatsReserved, demandeId);

        int threshold = demande.getSeuilPersonnalise() != null ? demande.getSeuilPersonnalise() : defaultThreshold;
        if (demande.getTotalSeatsReserved() >= threshold && demande.getStatus() == DemandeStatus.OPEN) {
            triggerDemande(demande, threshold);
        }
        return demande;
    }

    private void triggerDemande(DemandeCollective demande, int thresholdUsed) {
        demande.setStatus(DemandeStatus.TRIGGERED);
        demande.setTriggeredAt(LocalDateTime.now());
        demandeRepo.save(demande);

        DemandeThresholdReachedEvent event = new DemandeThresholdReachedEvent(
                demande.getId(),
                demande.getOrganisateurId(),
                demande.getDepartureCity(),
                demande.getArrivalCity(),
                demande.getRequestedDate(),
                demande.getTotalSeatsReserved(),
                thresholdUsed
        );
        eventPublisher.publishThresholdReached(event);
        log.info("Demande {} déclenchée (seuil atteint: {} places)", demande.getId(), thresholdUsed);
    }

    // ==================== MISE À JOUR ====================
    @Transactional
    public DemandeCollective updateDemande(String organisateurId, String demandeId, UpdateDemandeRequest request) {
        DemandeCollective demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée: " + demandeId));

        if (!demande.getOrganisateurId().equals(organisateurId)) {
            throw new BusinessException("Vous n'êtes pas le créateur de cette demande");
        }
        if (demande.getStatus() != DemandeStatus.OPEN) {
            throw new BusinessException("Seules les demandes OPEN peuvent être modifiées");
        }

        if (request.getDepartureCity() != null) demande.setDepartureCity(request.getDepartureCity());
        if (request.getArrivalCity() != null) demande.setArrivalCity(request.getArrivalCity());
        if (request.getRequestedDate() != null) demande.setRequestedDate(request.getRequestedDate());
        if (request.getVehicleType() != null) {
            demande.setVehicleType(request.getVehicleType());
            demande.setTotalCapacity(request.getVehicleType().getCapacity());
        }
        if (request.getSeuilPersonnalise() != null) {
            if (request.getSeuilPersonnalise() > demande.getTotalCapacity()) {
                throw new BusinessException("Le seuil ne peut pas dépasser la capacité du véhicule");
            }
            demande.setSeuilPersonnalise(request.getSeuilPersonnalise());
        }

        demande = demandeRepo.save(demande);
        log.info("Demande {} mise à jour par organisateur {}", demandeId, organisateurId);
        return demande;
    }

    // ==================== ANNULATION (organisateur uniquement) ====================
    @Transactional
    public void cancelDemande(String organisateurId, String demandeId) {
        DemandeCollective demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée: " + demandeId));

        if (!demande.getOrganisateurId().equals(organisateurId)) {
            throw new BusinessException("Vous n'êtes pas le créateur de cette demande");
        }
        if (demande.getStatus() == DemandeStatus.CANCELLED || demande.getStatus() == DemandeStatus.CLOSED) {
            throw new BusinessException("Cette demande est déjà fermée ou annulée");
        }
        if (demande.getTotalSeatsReserved() > 0) {
            throw new BusinessException("Impossible d'annuler: des passagers ont déjà rejoint cette demande");
        }

        demande.setStatus(DemandeStatus.CANCELLED);
        demandeRepo.save(demande);
        log.info("Demande {} annulée par organisateur {}", demandeId, organisateurId);
    }

    // ==================== RECHERCHE ====================
    @Transactional(readOnly = true)
    public List<DemandeCollective> searchOpenDemandes(DemandeSearchRequest request) {
        if (request.getRequestedDate() != null) {
            return demandeRepo.findByDepartureCityAndArrivalCityAndRequestedDateAndStatus(
                    request.getDepartureCity(), request.getArrivalCity(),
                    request.getRequestedDate(), DemandeStatus.OPEN);
        } else {
            return demandeRepo.findByDepartureCityAndArrivalCityAndStatus(
                    request.getDepartureCity(), request.getArrivalCity(), DemandeStatus.OPEN);
        }
    }

    @Transactional(readOnly = true)
    public Page<DemandeCollective> searchOpenDemandes(DemandeSearchRequest request, Pageable pageable) {
        if (request.getRequestedDate() != null) {
            return demandeRepo.findByDepartureCityAndArrivalCityAndRequestedDateAndStatus(
                    request.getDepartureCity(), request.getArrivalCity(),
                    request.getRequestedDate(), DemandeStatus.OPEN, pageable);
        } else {
            return demandeRepo.findByDepartureCityAndArrivalCityAndStatus(
                    request.getDepartureCity(), request.getArrivalCity(), DemandeStatus.OPEN, pageable);
        }
    }

    @Transactional(readOnly = true)
    public DemandeCollective getDemandeById(String demandeId) {
        return demandeRepo.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée: " + demandeId));
    }

    @Transactional(readOnly = true)
    public List<DemandeCollective> getDemandesByOrganisateur(String organisateurId) {
        return demandeRepo.findByOrganisateurId(organisateurId);
    }

    @Transactional(readOnly = true)
    public Page<DemandeCollective> getDemandesByOrganisateur(String organisateurId, Pageable pageable) {
        return demandeRepo.findByOrganisateurId(organisateurId, pageable);
    }

    @Transactional(readOnly = true)
    public List<String> getWaitingPassengersByRoute(String departureCity, String arrivalCity) {
        return passagerRepo.findWaitingPassengerIdsByRoute(departureCity, arrivalCity);
    }

    // ==================== CLÔTURE (organisateur uniquement) ====================
    @Transactional
    public void closeDemande(String organisateurId, String demandeId) {
        DemandeCollective demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée: " + demandeId));

        if (!demande.getOrganisateurId().equals(organisateurId)) {
            throw new BusinessException("Vous n'êtes pas le créateur de cette demande");
        }
        if (demande.getStatus() == DemandeStatus.CLOSED) {
            throw new BusinessException("Cette demande est déjà fermée");
        }

        demande.setStatus(DemandeStatus.CLOSED);
        demandeRepo.save(demande);
        log.info("Demande {} fermée par organisateur {}", demandeId, organisateurId);
    }

    // ==================== ADMIN INTERNAL METHODS ====================
    @Transactional
    public void adminCloseDemande(String demandeId) {
        DemandeCollective demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée: " + demandeId));
        if (demande.getStatus() == DemandeStatus.CLOSED) {
            throw new BusinessException("Cette demande est déjà fermée");
        }
        demande.setStatus(DemandeStatus.CLOSED);
        demandeRepo.save(demande);
        log.info("Demande {} fermée par admin", demandeId);
    }

    @Transactional
    public void adminCancelDemande(String demandeId) {
        DemandeCollective demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée: " + demandeId));
        if (demande.getStatus() == DemandeStatus.CANCELLED || demande.getStatus() == DemandeStatus.CLOSED) {
            throw new BusinessException("Cette demande est déjà fermée ou annulée");
        }
        demande.setStatus(DemandeStatus.CANCELLED);
        demandeRepo.save(demande);
        log.info("Demande {} annulée par admin", demandeId);
    }

    // ==================== CONVERSION EN TRAJET ====================
    @Transactional
    @Retry(name = "trajetService")
    public void convertDemandeToTrajet(String driverId, String demandeId, ConvertDemandeRequest request) {
        DemandeCollective demande = demandeRepo.findById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée: " + demandeId));

        if (demande.getStatus() != DemandeStatus.TRIGGERED) {
            throw new BusinessException("Seules les demandes déclenchées peuvent être converties en trajet");
        }

        CreateTrajetFromDemandeRequest trajetRequest = buildTrajetRequest(demande, request);

        trajetServiceWebClient.post()
                .uri("/api/v1/trajets")
                .header("X-User-Id", driverId)
                .header("X-User-Role", "DRIVER")
                .bodyValue(trajetRequest)
                .retrieve()
                .bodyToMono(Void.class)
                .block(Duration.ofSeconds(5));

        demande.setStatus(DemandeStatus.CLOSED);
        demandeRepo.save(demande);

        java.util.List<String> passengerIds = passagerRepo.findByDemandeId(demandeId).stream()
                .map(dp -> dp.getPassengerId())
                .toList();
        eventPublisher.publishConverted(demandeId, driverId, passengerIds,
                demande.getDepartureCity(), demande.getArrivalCity());

        log.info("Demande {} convertie en trajet par chauffeur {}, {} passagers notifiés",
                demandeId, driverId, passengerIds.size());
    }

    private CreateTrajetFromDemandeRequest buildTrajetRequest(DemandeCollective demande, ConvertDemandeRequest request) {
        LocalDateTime departureDate = request.getDepartureDate() != null
                ? request.getDepartureDate()
                : demande.getRequestedDate().atTime(10, 0);

        BigDecimal pricePerSeat = request.getPricePerSeat() != null
                ? request.getPricePerSeat()
                : BigDecimal.valueOf(10);

        CreateTrajetFromDemandeRequest trajetRequest = new CreateTrajetFromDemandeRequest();
        trajetRequest.setDepartureCity(demande.getDepartureCity());
        trajetRequest.setArrivalCity(demande.getArrivalCity());
        trajetRequest.setDepartureDate(departureDate);
        trajetRequest.setTotalSeats(demande.getTotalCapacity());
        trajetRequest.setPricePerSeat(pricePerSeat);
        trajetRequest.setAcceptsPets(false);
        trajetRequest.setAirConditioned(true);
        trajetRequest.setHasIntermediateStops(false);
        return trajetRequest;
    }
}