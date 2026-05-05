package com.moussefer.demande.service;

import com.moussefer.demande.dto.MergeDemandeResponse;
import com.moussefer.demande.entity.DemandeCollective;
import com.moussefer.demande.entity.DemandePassager;
import com.moussefer.demande.entity.DemandeStatus;
import com.moussefer.demande.entity.VehicleType;
import com.moussefer.demande.kafka.DemandeEventPublisher;
import com.moussefer.demande.repository.DemandePassagerRepository;
import com.moussefer.demande.repository.DemandeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DemandeMergeService {

    private final DemandeRepository demandeRepo;
    private final DemandePassagerRepository passagerRepo;
    private final DemandeEventPublisher eventPublisher;

    // ✅ FIX: Removed 'final' so @RequiredArgsConstructor skips it
    // Now Spring uses field injection (after construction) instead of constructor injection
    @Lazy
    @Autowired
    private DemandeMergeService self;   // self-proxy to enable transaction on scheduled calls

    /**
     * Scheduled automatic merge — runs every hour.
     */
    @Scheduled(fixedDelayString = "${demande.merge.interval-ms:3600000}")
    public void scheduledMerge() {
        log.info("FEAT-01: scheduled merge scan starting");
        int total = self.runMergeForAllRoutes();   // call proxy to apply @Transactional
        log.info("FEAT-01: scheduled merge scan done — {} demands merged", total);
    }

    @Transactional
    public List<MergeDemandeResponse> mergeAll() {
        log.info("FEAT-01: manual mergeAll triggered");
        List<MergeDemandeResponse> results = new ArrayList<>();

        List<DemandeCollective> openDemandes = demandeRepo.findByStatus(DemandeStatus.OPEN);
        List<String> processed = new ArrayList<>();

        for (DemandeCollective candidate : openDemandes) {
            if (processed.contains(candidate.getId())) continue;

            LocalDate dateFrom = candidate.getRequestedDate().minusDays(1);
            LocalDate dateTo   = candidate.getRequestedDate().plusDays(1);

            List<DemandeCollective> similar = demandeRepo.findSimilarOpenDemandes(
                    candidate.getDepartureCity(),
                    candidate.getArrivalCity(),
                    candidate.getVehicleType(),
                    dateFrom,
                    dateTo
            );

            if (similar.size() < 2) continue;

            MergeDemandeResponse result = mergeSimilarGroup(similar);
            if (result != null) {
                results.add(result);
                similar.forEach(d -> processed.add(d.getId()));
            }
        }
        return results;
    }

    @Transactional
    public MergeDemandeResponse mergeByRoute(String departureCity, String arrivalCity,
                                             VehicleType vehicleType, LocalDate requestedDate) {
        LocalDate dateFrom = requestedDate.minusDays(1);
        LocalDate dateTo   = requestedDate.plusDays(1);

        List<DemandeCollective> similar = demandeRepo.findSimilarOpenDemandes(
                departureCity, arrivalCity, vehicleType, dateFrom, dateTo);

        if (similar.size() < 2) {
            return MergeDemandeResponse.builder()
                    .message("Aucune demande similaire à fusionner (< 2 trouvées)")
                    .mergedDemandeIds(List.of())
                    .totalPassengersMoved(0)
                    .build();
        }
        return mergeSimilarGroup(similar);
    }

    private MergeDemandeResponse mergeSimilarGroup(List<DemandeCollective> group) {
        DemandeCollective survivor = group.get(0);
        List<DemandeCollective> toMerge = group.subList(1, group.size());

        List<String> mergedIds = new ArrayList<>();
        int totalMoved = 0;
        List<String> allPassengerIds = new ArrayList<>();

        passagerRepo.findByDemandeId(survivor.getId())
                .forEach(p -> allPassengerIds.add(p.getPassengerId()));

        for (DemandeCollective source : toMerge) {
            List<DemandePassager> passengers = passagerRepo.findByDemandeId(source.getId());

            for (DemandePassager p : passengers) {
                boolean alreadyIn = passagerRepo.existsByDemandeIdAndPassengerId(
                        survivor.getId(), p.getPassengerId());
                if (alreadyIn) continue;

                int currentSeats = passagerRepo.sumSeatsReservedByDemandeId(survivor.getId());
                if (currentSeats + p.getSeatsReserved() > survivor.getTotalCapacity()) {
                    log.warn("FEAT-01: capacity reached on survivor {} — skipping passenger {}",
                            survivor.getId(), p.getPassengerId());
                    continue;
                }

                DemandePassager moved = DemandePassager.builder()
                        .demandeId(survivor.getId())
                        .passengerId(p.getPassengerId())
                        .seatsReserved(p.getSeatsReserved())
                        .build();
                passagerRepo.save(moved);
                allPassengerIds.add(p.getPassengerId());
                totalMoved++;
            }

            int newTotal = passagerRepo.sumSeatsReservedByDemandeId(survivor.getId());
            survivor.setTotalSeatsReserved(newTotal);
            demandeRepo.save(survivor);

            source.setStatus(DemandeStatus.MERGED);
            demandeRepo.save(source);
            mergedIds.add(source.getId());

            log.info("FEAT-01: merged demandeId={} into survivorId={}", source.getId(), survivor.getId());
        }

        if (!mergedIds.isEmpty()) {
            eventPublisher.publishMerged(survivor.getId(), mergedIds, allPassengerIds,
                    survivor.getDepartureCity(), survivor.getArrivalCity());
        }

        int seatsAfterMerge = passagerRepo.sumSeatsReservedByDemandeId(survivor.getId());

        return MergeDemandeResponse.builder()
                .survivorDemandeId(survivor.getId())
                .mergedDemandeIds(mergedIds)
                .totalPassengersMoved(totalMoved)
                .totalSeatsAfterMerge(seatsAfterMerge)
                .message(String.format("%d demande(s) fusionnée(s) dans %s", mergedIds.size(), survivor.getId()))
                .build();
    }

    @Transactional
    public int runMergeForAllRoutes() {
        try {
            List<MergeDemandeResponse> results = mergeAll();
            return results.stream().mapToInt(r -> r.getMergedDemandeIds().size()).sum();
        } catch (Exception e) {
            log.error("FEAT-01: scheduled merge error: {}", e.getMessage(), e);
            return 0;
        }
    }
}