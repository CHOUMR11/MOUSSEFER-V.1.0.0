package com.moussefer.avis.repository;

import com.moussefer.avis.entity.Avis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AvisRepository extends JpaRepository<Avis, String> {

    List<Avis> findByDriverIdOrderByCreatedAtDesc(String driverId);

    Optional<Avis> findByReservationId(String reservationId);

    boolean existsByReservationId(String reservationId);

    @Query("SELECT AVG(a.rating) FROM Avis a WHERE a.driverId = :driverId")
    Double computeAverageRating(String driverId);

    @Query("SELECT COUNT(a) FROM Avis a WHERE a.driverId = :driverId")
    long countByDriver(String driverId);

    boolean existsByDriverIdAndPassengerIdAndTrajetId(String driverId, String passengerId, String trajetId);
}