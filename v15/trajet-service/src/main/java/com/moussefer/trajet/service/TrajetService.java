package com.moussefer.trajet.service;

import com.moussefer.trajet.dto.external.TrajetInfoResponse;
import com.moussefer.trajet.dto.request.CreateTrajetRequest;
import com.moussefer.trajet.dto.request.SearchTrajetRequest;
import com.moussefer.trajet.dto.request.UpdateTrajetRequest;
import com.moussefer.trajet.dto.response.TrajetResponse;
import com.moussefer.trajet.entity.*;
import com.moussefer.trajet.exception.BusinessException;
import com.moussefer.trajet.exception.ResourceNotFoundException;
import com.moussefer.trajet.kafka.TrajetEventProducer;
import com.moussefer.trajet.repository.TrajetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrajetService {

    private final TrajetRepository trajetRepository;
    private final TrajetEventProducer eventProducer;

    // ─── Driver: publish a new departure ─────────────────────────────────
    @Transactional
    public TrajetResponse publishTrajet(String driverId, CreateTrajetRequest req) {
        LocalDateTime depDate = req.getDepartureDate();
        LocalDateTime dayStart = depDate.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd   = dayStart.plusDays(1).minusSeconds(1);

        int maxAttempts = 3;
        int attempt = 1;
        while (attempt <= maxAttempts) {
            try {
                int priorityOrder = allocatePriorityOrder(req.getDepartureCity(), req.getArrivalCity(), dayStart, dayEnd);
                TrajetStatus initialStatus = priorityOrder == 1 ? TrajetStatus.ACTIVE : TrajetStatus.LOCKED;

                Trajet trajet = Trajet.builder()
                        .driverId(driverId)
                        .departureCity(req.getDepartureCity())
                        .arrivalCity(req.getArrivalCity())
                        .departureDate(depDate)
                        .totalSeats(req.getTotalSeats())
                        .availableSeats(req.getTotalSeats())
                        .status(initialStatus)
                        .priorityOrder(priorityOrder)
                        .acceptsPets(req.isAcceptsPets())
                        .allowsLargeBags(req.isAllowsLargeBags())
                        .airConditioned(req.isAirConditioned())
                        .hasIntermediateStops(req.isHasIntermediateStops())
                        .directTrip(req.isDirectTrip())
                        .pricePerSeat(req.getPricePerSeat())
                        .notes(req.getNotes())
                        .build();

                trajet = trajetRepository.saveAndFlush(trajet);
                log.info("Trajet published: id={} driver={} priority={} status={}",
                        trajet.getId(), driverId, priorityOrder, initialStatus);

                eventProducer.sendTrajetPublished(trajet);
                evictTrajetSearchCache();
                return toResponse(trajet, true);
            } catch (DataIntegrityViolationException ex) {
                if (attempt == maxAttempts) {
                    throw new BusinessException("Failed to publish trajet due to concurrent updates. Please retry.");
                }
                attempt++;
            }
        }
        throw new BusinessException("Failed to publish trajet");
    }

    // ─── Passenger search (cached, non-paginated) ───────────────────────
    @Cacheable(value = "trajetSearch", key = "#req.departureCity + ':' + #req.arrivalCity + ':' + #req.date + ':' + #req.seatsNeeded")
    @Transactional(readOnly = true)
    public List<TrajetResponse> search(SearchTrajetRequest req) {
        LocalDateTime from, to;
        if (req.getDate() != null) {
            from = req.getDate().atStartOfDay();
            to   = from.plusDays(1).minusSeconds(1);
        } else {
            from = LocalDateTime.now();
            to   = from.plusDays(30);
        }

        List<Trajet> trajets = trajetRepository.findByRouteAndDate(
                req.getDepartureCity(), req.getArrivalCity(), from, to);

        boolean activeFound = trajets.stream().anyMatch(t -> t.getStatus() == TrajetStatus.ACTIVE);
        return trajets.stream()
                .filter(t -> t.getAvailableSeats() >= req.getSeatsNeeded())
                .filter(t -> matchesVehicleOptions(t, req))
                .filter(t -> matchesPriceRange(t, req))
                .filter(t -> matchesTimeOfDay(t, req.getTimeOfDay()))
                .map(t -> toResponse(t, activeFound && t.getStatus() == TrajetStatus.ACTIVE))
                .collect(Collectors.toList());
    }

    private boolean matchesVehicleOptions(Trajet t, SearchTrajetRequest req) {
        if (req.getAcceptsPets() != null && !req.getAcceptsPets().equals(t.isAcceptsPets())) return false;
        if (req.getAirConditioned() != null && !req.getAirConditioned().equals(t.isAirConditioned())) return false;
        if (req.getAllowsLargeBags() != null && !req.getAllowsLargeBags().equals(t.isAllowsLargeBags())) return false;
        if (req.getDirectTrip() != null && !req.getDirectTrip().equals(t.isDirectTrip())) return false;
        return true;
    }

    private boolean matchesPriceRange(Trajet t, SearchTrajetRequest req) {
        if (t.getPricePerSeat() == null) return true;
        if (req.getPriceMin() != null && t.getPricePerSeat().compareTo(req.getPriceMin()) < 0) return false;
        if (req.getPriceMax() != null && t.getPricePerSeat().compareTo(req.getPriceMax()) > 0) return false;
        return true;
    }

    private boolean matchesTimeOfDay(Trajet t, String timeOfDay) {
        if (timeOfDay == null || timeOfDay.isBlank()) return true;
        int hour = t.getDepartureDate().getHour();
        return switch (timeOfDay.toLowerCase()) {
            case "morning"   -> hour >= 5  && hour < 12;
            case "afternoon" -> hour >= 12 && hour < 18;
            case "evening"   -> hour >= 18 || hour < 5;
            default -> true;
        };
    }

    @Transactional(readOnly = true)
    public TrajetResponse getById(String trajetId) {
        Trajet t = findOrThrow(trajetId);
        return toResponse(t, t.getStatus() == TrajetStatus.ACTIVE);
    }

    // ─── Driver: update trajet details ──────────────────────────────────
    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public TrajetResponse updateTrajet(String driverId, String trajetId, UpdateTrajetRequest req) {
        Trajet trajet = findOrThrow(trajetId);

        if (!trajet.getDriverId().equals(driverId)) {
            throw new BusinessException("Not your trajet");
        }
        if (trajet.getStatus() != TrajetStatus.ACTIVE && trajet.getStatus() != TrajetStatus.LOCKED) {
            throw new BusinessException("Cannot update trajet with status: " + trajet.getStatus());
        }

        if (req.getPricePerSeat() != null) {
            trajet.setPricePerSeat(req.getPricePerSeat());
        }
        if (req.getVehicleDescription() != null) {
            trajet.setNotes(req.getVehicleDescription());
        }

        trajetRepository.save(trajet);
        log.info("Trajet updated: id={} driver={}", trajetId, driverId);
        return toResponse(trajet, trajet.getStatus() == TrajetStatus.ACTIVE);
    }

    // ─── Driver: list own trajets ───────────────────────────────────────
    @Transactional(readOnly = true)
    public List<TrajetResponse> getMyTrajets(String driverId) {
        List<Trajet> trajets = trajetRepository.findByDriverIdOrderByCreatedAtDesc(driverId);
        return trajets.stream()
                .map(t -> toResponse(t, t.getStatus() == TrajetStatus.ACTIVE))
                .collect(Collectors.toList());
    }

    // ─── Driver: cancel own trajet ──────────────────────────────────────
    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public void driverCancelTrajet(String driverId, String trajetId) {
        Trajet trajet = findOrThrow(trajetId);
        if (!trajet.getDriverId().equals(driverId)) {
            throw new BusinessException("Not your trajet");
        }
        boolean wasActive = trajet.getStatus() == TrajetStatus.ACTIVE;
        if (!wasActive && trajet.getStatus() != TrajetStatus.LOCKED) {
            throw new BusinessException("Cannot cancel trajet with status: " + trajet.getStatus());
        }
        trajet.setStatus(TrajetStatus.CANCELLED);
        trajetRepository.saveAndFlush(trajet);
        log.info("Trajet {} cancelled by driver {}", trajetId, driverId);

        eventProducer.sendTrajetCancelled(trajet);

        if (wasActive) {
            promoteNextInQueue(trajet);
        }
    }

    // ─── Internal endpoint for reservation-service ─────────────────────
    @Transactional(readOnly = true)
    public TrajetInfoResponse getTrajetInfoForInternal(String trajetId) {
        Trajet trajet = findOrThrow(trajetId);
        return TrajetInfoResponse.builder()
                .id(trajet.getId())
                .driverId(trajet.getDriverId())
                .pricePerSeat(trajet.getPricePerSeat())
                .departureCity(trajet.getDepartureCity())
                .arrivalCity(trajet.getArrivalCity())
                .availableSeats(trajet.getAvailableSeats() - trajet.getReservedSeats())
                .status(trajet.getStatus().name())
                .departureDate(trajet.getDepartureDate())
                .build();
    }

    // ─── Driver actions ────────────────────────────────────────────────
    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public TrajetResponse markDeparted(String driverId, String trajetId) {
        Trajet trajet = findOrThrow(trajetId);
        if (!trajet.getDriverId().equals(driverId)) {
            throw new BusinessException("Not your trajet");
        }
        if (trajet.getStatus() != TrajetStatus.ACTIVE && trajet.getStatus() != TrajetStatus.FULL) {
            throw new BusinessException("Cannot mark departed — trajet status is: " + trajet.getStatus());
        }
        trajet.setStatus(TrajetStatus.DEPARTED);
        trajet.setDepartedAt(LocalDateTime.now());
        trajetRepository.save(trajet);

        promoteNextInQueue(trajet);
        log.info("Trajet departed: id={}", trajetId);
        eventProducer.sendTrajetDeparted(trajet);
        return toResponse(trajet, false);
    }

    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public TrajetResponse reduceSeats(String driverId, String trajetId, int seats) {
        Trajet trajet = findOrThrow(trajetId);
        if (!trajet.getDriverId().equals(driverId)) {
            throw new BusinessException("Not your trajet");
        }
        int updated = trajetRepository.decrementSeatsWithVersion(trajetId, seats, trajet.getVersion());
        if (updated == 0) {
            throw new BusinessException("Not enough available seats or concurrent modification");
        }
        trajet = findOrThrow(trajetId);
        checkIfFull(trajet);
        return toResponse(trajet, trajet.getStatus() == TrajetStatus.ACTIVE);
    }

    // ─── Called by Kafka consumer when reservation escalated ──────────
    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public void activateNextTrajetForRoute(String escalatedTrajetId) {
        Trajet escalatedTrajet = findOrThrow(escalatedTrajetId);
        LocalDateTime dayStart = escalatedTrajet.getDepartureDate().toLocalDate().atStartOfDay();
        LocalDateTime dayEnd   = dayStart.plusDays(1).minusSeconds(1);

        trajetRepository.findNextInQueue(
                escalatedTrajet.getDepartureCity(),
                escalatedTrajet.getArrivalCity(),
                dayStart,
                dayEnd,
                PageRequest.of(0, 1)
        ).stream().findFirst().ifPresent(next -> {
            next.setStatus(TrajetStatus.ACTIVE);
            trajetRepository.save(next);
            log.info("Activated next trajet after escalation: id={} priority={}", next.getId(), next.getPriorityOrder());
            eventProducer.sendTrajetActivated(next);
        });
    }

    // ─── Admin actions (called by admin-service via internal endpoints) ──
    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public void adminCancelTrajet(String trajetId) {
        Trajet trajet = findOrThrow(trajetId);
        boolean wasActive = trajet.getStatus() == TrajetStatus.ACTIVE;
        trajet.setStatus(TrajetStatus.CANCELLED);
        trajetRepository.save(trajet);
        log.info("Trajet {} cancelled by admin", trajetId);
        eventProducer.sendTrajetCancelled(trajet);
        if (wasActive) {
            promoteNextInQueue(trajet);
        }
    }

    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public TrajetResponse adminAssignDriver(String trajetId, String newDriverId) {
        Trajet trajet = findOrThrow(trajetId);
        if (trajet.getStatus() == TrajetStatus.CANCELLED || trajet.getStatus() == TrajetStatus.DEPARTED) {
            throw new BusinessException("Cannot reassign driver on a cancelled/departed trajet");
        }
        String oldDriverId = trajet.getDriverId();
        trajet.setDriverId(newDriverId);
        trajetRepository.save(trajet);
        log.info("Admin reassigned trajet {} from driver {} to {}", trajetId, oldDriverId, newDriverId);
        return toResponse(trajet, trajet.getStatus() == TrajetStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TrajetResponse> adminListAll(
            String status, String driverId, String departureCity,
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<Trajet> spec = (root, query, cb) -> {
            var preds = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) {
                try { preds.add(cb.equal(root.get("status"), TrajetStatus.valueOf(status.toUpperCase()))); }
                catch (IllegalArgumentException e) { throw new BusinessException("Invalid status: " + status); }
            }
            if (driverId != null) preds.add(cb.equal(root.get("driverId"), driverId));
            if (departureCity != null) preds.add(cb.equal(root.get("departureCity"), departureCity));
            return cb.and(preds.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return trajetRepository.findAll(spec, pageable).map(t -> toResponse(t, t.getStatus() == TrajetStatus.ACTIVE));
    }

    // ─── Seat reservation methods (used by reservation-service) ─────────
    @Transactional
    public void reserveSeatsTemporarily(String trajetId, int seats) {
        int updated = trajetRepository.reserveSeats(trajetId, seats);
        if (updated == 0) {
            throw new BusinessException("Not enough seats available for temporary reservation");
        }
        log.info("Temporarily reserved {} seats for trajet {}", seats, trajetId);
    }

    @Transactional
    public void confirmReservationSeats(String trajetId, int seats) {
        int updated = trajetRepository.confirmSeats(trajetId, seats);
        if (updated == 0) {
            throw new BusinessException("Failed to confirm seats – concurrent modification");
        }
        Trajet trajet = findOrThrow(trajetId);
        checkIfFull(trajet);
        log.info("Confirmed reservation seats: trajet={}, seats={}", trajetId, seats);
    }

    @Transactional
    public void releaseTemporaryReservation(String trajetId, int seats) {
        int updated = trajetRepository.releaseReservedSeats(trajetId, seats);
        if (updated == 0) {
            log.warn("Failed to release temporary seats for trajet {} – may have already been confirmed", trajetId);
        } else {
            log.info("Released {} temporary seats for trajet {}", seats, trajetId);
        }
    }

    // ─── Internal service-to-service seat management (legacy) ────────────
    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public void internalReduceSeats(String trajetId, int seats) {
        Trajet trajet = findOrThrow(trajetId);
        int updated = trajetRepository.decrementSeatsWithVersion(trajetId, seats, trajet.getVersion());
        if (updated == 0) {
            throw new BusinessException("Not enough available seats or concurrent modification");
        }
        trajet = findOrThrow(trajetId);
        checkIfFull(trajet);
        log.info("Internal: reduced {} seats for trajet {}", seats, trajetId);
    }

    @CacheEvict(value = "trajetSearch", allEntries = true)
    @Transactional
    public void internalIncrementSeats(String trajetId, int seats) {
        trajetRepository.incrementSeats(trajetId, seats);
        log.info("Internal: restored {} seats for trajet {}", seats, trajetId);
    }

    // ─── Internal helpers ─────────────────────────────────────────────
    private void promoteNextInQueue(Trajet current) {
        LocalDateTime dayStart = current.getDepartureDate().toLocalDate().atStartOfDay();
        LocalDateTime dayEnd   = dayStart.plusDays(1).minusSeconds(1);

        trajetRepository.findNextInQueue(
                current.getDepartureCity(),
                current.getArrivalCity(),
                dayStart,
                dayEnd,
                PageRequest.of(0, 1)
        ).stream().findFirst().ifPresent(next -> {
            next.setStatus(TrajetStatus.ACTIVE);
            trajetRepository.save(next);
            log.info("Promoted trajet to ACTIVE: id={} priority={}", next.getId(), next.getPriorityOrder());
            eventProducer.sendTrajetActivated(next);
            evictTrajetSearchCache();
        });
    }

    private void checkIfFull(Trajet trajet) {
        if (trajet.getAvailableSeats() == 0) {
            trajet.setStatus(TrajetStatus.FULL);
            trajetRepository.save(trajet);
            log.info("Trajet is now FULL: id={}", trajet.getId());
            promoteNextInQueue(trajet);
        }
    }

    private Trajet findOrThrow(String id) {
        return trajetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trajet not found: " + id));
    }

    private int allocatePriorityOrder(String dep, String arr, LocalDateTime from, LocalDateTime to) {
        var existing = trajetRepository.lockRouteForDate(dep, arr, from, to);
        return existing.isEmpty() ? 1 : existing.getFirst().getPriorityOrder() + 1;
    }

    private TrajetResponse toResponse(Trajet t, boolean reservable) {
        return TrajetResponse.from(t, reservable);
    }

    @CacheEvict(value = "trajetSearch", allEntries = true)
    public void evictTrajetSearchCache() {
        log.debug("Trajet search cache evicted");
    }
}