package com.moussefer.station.repository;

import com.moussefer.station.entity.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StationRepository extends JpaRepository<Station, String> {
    List<Station> findByActiveTrue();
    List<Station> findByCityIgnoreCase(String city);
    List<Station> findByRegionIgnoreCase(String region);
    boolean existsByNameIgnoreCaseAndCityIgnoreCase(String name, String city);
}
