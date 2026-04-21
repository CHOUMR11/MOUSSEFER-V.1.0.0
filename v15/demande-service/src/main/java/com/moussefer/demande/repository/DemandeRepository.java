package com.moussefer.demande.repository;

import com.moussefer.demande.entity.DemandeCollective;
import com.moussefer.demande.entity.DemandeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DemandeRepository extends JpaRepository<DemandeCollective, String> {

    Optional<DemandeCollective> findByIdAndStatus(String id, DemandeStatus status);

    List<DemandeCollective> findByStatus(DemandeStatus status);

    List<DemandeCollective> findByOrganisateurId(String organisateurId);

    Page<DemandeCollective> findByOrganisateurId(String organisateurId, Pageable pageable);

    List<DemandeCollective> findByDepartureCityAndArrivalCityAndStatus(
            String dep, String arr, DemandeStatus status);

    Page<DemandeCollective> findByDepartureCityAndArrivalCityAndStatus(
            String dep, String arr, DemandeStatus status, Pageable pageable);

    List<DemandeCollective> findByDepartureCityAndArrivalCityAndRequestedDateAndStatus(
            String dep, String arr, LocalDate date, DemandeStatus status);

    Page<DemandeCollective> findByDepartureCityAndArrivalCityAndRequestedDateAndStatus(
            String dep, String arr, LocalDate date, DemandeStatus status, Pageable pageable);

    // FEAT-01: Find similar open demands for merge (same route, vehicle type, date ±1 day)
    @org.springframework.data.jpa.repository.Query("""
        SELECT d FROM DemandeCollective d
        WHERE d.status = 'OPEN'
          AND d.departureCity = :dep
          AND d.arrivalCity = :arr
          AND d.vehicleType = :vehicleType
          AND d.requestedDate BETWEEN :dateFrom AND :dateTo
        ORDER BY d.createdAt ASC
    """)
    List<DemandeCollective> findSimilarOpenDemandes(
            @org.springframework.data.repository.query.Param("dep") String dep,
            @org.springframework.data.repository.query.Param("arr") String arr,
            @org.springframework.data.repository.query.Param("vehicleType") com.moussefer.demande.entity.VehicleType vehicleType,
            @org.springframework.data.repository.query.Param("dateFrom") java.time.LocalDate dateFrom,
            @org.springframework.data.repository.query.Param("dateTo") java.time.LocalDate dateTo);

}