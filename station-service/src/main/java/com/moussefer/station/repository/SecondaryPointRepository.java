package com.moussefer.station.repository;

import com.moussefer.station.entity.SecondaryPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecondaryPointRepository extends JpaRepository<SecondaryPoint, String> {
    List<SecondaryPoint> findByStationIdAndActiveTrueOrderByDisplayOrderAsc(String stationId);
    List<SecondaryPoint> findByStationId(String stationId);
    void deleteByStationId(String stationId);
}
