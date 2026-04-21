package com.moussefer.station.service;

import com.moussefer.station.dto.request.StationRequest;
import com.moussefer.station.entity.SecondaryPoint;
import com.moussefer.station.entity.Station;
import com.moussefer.station.exception.ResourceNotFoundException;
import com.moussefer.station.repository.SecondaryPointRepository;
import com.moussefer.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StationService {

    private final StationRepository stationRepository;
    private final SecondaryPointRepository secondaryPointRepository;

    public List<Station> getAll() {
        return stationRepository.findAll();
    }

    public List<Station> getAllActive() {
        return stationRepository.findByActiveTrue();
    }

    public Station getById(String id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Station", "id", id));
    }

    public List<Station> getByCity(String city) {
        return stationRepository.findByCityIgnoreCase(city);
    }

    @Transactional
    public Station create(StationRequest request) {
        if (stationRepository.existsByNameIgnoreCaseAndCityIgnoreCase(request.getName(), request.getCity())) {
            throw new IllegalArgumentException("Station already exists in this city");
        }
        Station station = Station.builder()
                .name(request.getName())
                .city(request.getCity())
                .region(request.getRegion())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .build();
        log.info("Creating station: {} in {}", station.getName(), station.getCity());
        return stationRepository.save(station);
    }

    @Transactional
    public Station update(String id, StationRequest request) {
        Station station = getById(id);
        station.setName(request.getName());
        station.setCity(request.getCity());
        station.setRegion(request.getRegion());
        station.setAddress(request.getAddress());
        station.setLatitude(request.getLatitude());
        station.setLongitude(request.getLongitude());
        return stationRepository.save(station);
    }

    @Transactional
    public void deactivate(String id) {
        Station station = getById(id);
        station.setActive(false);
        stationRepository.save(station);
        log.info("Station {} deactivated", id);
    }

    // ─── Secondary Points ────────────────────────────────────────────
    public List<SecondaryPoint> getSecondaryPoints(String stationId) {
        getById(stationId); // verify station exists
        return secondaryPointRepository.findByStationIdAndActiveTrueOrderByDisplayOrderAsc(stationId);
    }

    @Transactional
    public SecondaryPoint addSecondaryPoint(String stationId, String name, String address,
                                             Double latitude, Double longitude, int displayOrder) {
        getById(stationId); // verify station exists
        SecondaryPoint point = SecondaryPoint.builder()
                .stationId(stationId)
                .name(name)
                .address(address)
                .latitude(latitude)
                .longitude(longitude)
                .displayOrder(displayOrder)
                .build();
        log.info("Adding secondary point '{}' to station {}", name, stationId);
        return secondaryPointRepository.save(point);
    }

    @Transactional
    public void removeSecondaryPoint(String pointId) {
        secondaryPointRepository.deleteById(pointId);
        log.info("Secondary point {} removed", pointId);
    }

    // ─── Station Statistics ──────────────────────────────────────────
    public Map<String, Object> getStationStats(String stationId) {
        Station station = getById(stationId);
        long totalSecondaryPoints = secondaryPointRepository.findByStationId(stationId).size();
        return Map.of(
                "stationId", station.getId(),
                "stationName", station.getName(),
                "city", station.getCity(),
                "active", station.isActive(),
                "secondaryPointsCount", totalSecondaryPoints,
                "hasGpsCoordinates", station.getLatitude() != null && station.getLongitude() != null
        );
    }

    public Map<String, Object> getGlobalStats() {
        long total = stationRepository.count();
        long active = stationRepository.findByActiveTrue().size();
        long withGps = stationRepository.findAll().stream()
                .filter(s -> s.getLatitude() != null && s.getLongitude() != null).count();
        return Map.of(
                "totalStations", total,
                "activeStations", active,
                "inactiveStations", total - active,
                "stationsWithGps", withGps
        );
    }

    @Transactional
    public SecondaryPoint updateSecondaryPointOrder(String pointId, int displayOrder) {
        SecondaryPoint point = secondaryPointRepository.findById(pointId)
                .orElseThrow(() -> new ResourceNotFoundException("SecondaryPoint", "id", pointId));
        point.setDisplayOrder(displayOrder);
        log.info("US-115: secondary point {} displayOrder updated to {}", pointId, displayOrder);
        return secondaryPointRepository.save(point);
    }

}
