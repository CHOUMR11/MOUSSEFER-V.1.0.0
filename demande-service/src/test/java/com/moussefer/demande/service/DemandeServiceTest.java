package com.moussefer.demande.service;

import com.moussefer.demande.dto.DemandeCreationRequest;
import com.moussefer.demande.entity.DemandeCollective;
import com.moussefer.demande.entity.DemandeStatus;
import com.moussefer.demande.entity.VehicleType;
import com.moussefer.demande.exception.BusinessException;
import com.moussefer.demande.exception.ResourceNotFoundException;
import com.moussefer.demande.kafka.DemandeEventPublisher;
import com.moussefer.demande.repository.DemandePassagerRepository;
import com.moussefer.demande.repository.DemandeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests métier des demandes collectives.
 *
 * Garanties testées :
 *   1. Le seuil personnalisé ne peut jamais dépasser la capacité du véhicule
 *   2. Statut initial OPEN
 *   3. Anti-doublon : un passager ne peut pas rejoindre la même demande 2 fois
 *   4. Capacité atteinte : pas de nouvelle participation
 *   5. Demande inexistante → ResourceNotFoundException explicite
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DemandeService — règles métier des demandes collectives")
class DemandeServiceTest {

    @Mock private DemandeRepository demandeRepo;
    @Mock private DemandePassagerRepository passagerRepo;
    @Mock private DemandeEventPublisher eventPublisher;
    @Mock private WebClient trajetServiceWebClient;

    @InjectMocks private DemandeService demandeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(demandeService, "defaultThreshold", 4);
    }

    // ─────────── CRÉATION ───────────

    @Test
    @DisplayName("Création OK : statut OPEN, capacité du véhicule appliquée")
    void createDemande_success() {
        DemandeCreationRequest req = new DemandeCreationRequest();
        req.setDepartureCity("Tunis");
        req.setArrivalCity("Sousse");
        req.setRequestedDate(LocalDate.now().plusDays(2));
        req.setVehicleType(VehicleType.MINIBUS); // 16 places

        when(demandeRepo.save(any(DemandeCollective.class))).thenAnswer(inv -> {
            DemandeCollective d = inv.getArgument(0);
            d.setId("dem-1");
            return d;
        });

        DemandeCollective result = demandeService.createDemande("org-1", req);

        ArgumentCaptor<DemandeCollective> cap = ArgumentCaptor.forClass(DemandeCollective.class);
        verify(demandeRepo).save(cap.capture());
        DemandeCollective saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(DemandeStatus.OPEN);
        assertThat(saved.getTotalCapacity()).isEqualTo(16); // capacity du MINIBUS
        assertThat(saved.getOrganisateurId()).isEqualTo("org-1");
    }

    @Test
    @DisplayName("Seuil personnalisé > capacité véhicule → BusinessException")
    void createDemande_seuilTooHigh_rejected() {
        DemandeCreationRequest req = new DemandeCreationRequest();
        req.setDepartureCity("Tunis");
        req.setArrivalCity("Sfax");
        req.setRequestedDate(LocalDate.now().plusDays(3));
        req.setVehicleType(VehicleType.VOITURE_4); // 4 places max
        req.setSeuilPersonnalise(10); // demande 10 alors que voiture = 4

        assertThatThrownBy(() -> demandeService.createDemande("org-1", req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ne peut pas dépasser la capacité");

        verify(demandeRepo, never()).save(any());
    }

    @Test
    @DisplayName("Seuil personnalisé valide (<= capacité) → accepté")
    void createDemande_seuilValid_accepted() {
        DemandeCreationRequest req = new DemandeCreationRequest();
        req.setDepartureCity("Tunis");
        req.setArrivalCity("Sousse");
        req.setRequestedDate(LocalDate.now().plusDays(2));
        req.setVehicleType(VehicleType.VOITURE_8);
        req.setSeuilPersonnalise(6); // 6 <= 8 → OK

        when(demandeRepo.save(any(DemandeCollective.class))).thenAnswer(inv -> inv.getArgument(0));

        demandeService.createDemande("org-1", req);

        verify(demandeRepo).save(argThat(d ->
                d.getSeuilPersonnalise() != null && d.getSeuilPersonnalise() == 6));
    }

    // ─────────── PARTICIPATION (joinDemande) ───────────

    @Test
    @DisplayName("Demande inexistante → ResourceNotFoundException")
    void joinDemande_unknownDemande_throws() {
        when(demandeRepo.findByIdAndStatus("dem-unknown", DemandeStatus.OPEN))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                demandeService.joinDemande("pas-1", "dem-unknown", 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("non trouvée");
    }

    @Test
    @DisplayName("Anti-doublon : passager déjà inscrit → BusinessException")
    void joinDemande_alreadyJoined_rejected() {
        DemandeCollective open = DemandeCollective.builder()
                .id("dem-1").totalCapacity(16)
                .status(DemandeStatus.OPEN).build();
        when(demandeRepo.findByIdAndStatus("dem-1", DemandeStatus.OPEN))
                .thenReturn(Optional.of(open));
        when(passagerRepo.existsByDemandeIdAndPassengerId("dem-1", "pas-99"))
                .thenReturn(true);

        assertThatThrownBy(() -> demandeService.joinDemande("pas-99", "dem-1", 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà rejoint");
    }

    @Test
    @DisplayName("Capacité dépassée par cette participation → BusinessException")
    void joinDemande_overCapacity_rejected() {
        DemandeCollective almostFull = DemandeCollective.builder()
                .id("dem-2").totalCapacity(8)
                .status(DemandeStatus.OPEN).build();
        when(demandeRepo.findByIdAndStatus("dem-2", DemandeStatus.OPEN))
                .thenReturn(Optional.of(almostFull));
        when(passagerRepo.existsByDemandeIdAndPassengerId(anyString(), anyString()))
                .thenReturn(false);
        when(passagerRepo.sumSeatsReservedByDemandeId("dem-2")).thenReturn(7); // 7 déjà pris

        // pas-1 demande 3 places → 7+3=10 > 8 capacité
        assertThatThrownBy(() ->
                demandeService.joinDemande("pas-1", "dem-2", 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Capacité maximale");
    }
}
