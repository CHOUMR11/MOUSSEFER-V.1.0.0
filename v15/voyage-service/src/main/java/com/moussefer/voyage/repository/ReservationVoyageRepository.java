package com.moussefer.voyage.repository;

import com.moussefer.voyage.entity.ReservationVoyage;
import com.moussefer.voyage.entity.ReservationVoyageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationVoyageRepository extends JpaRepository<ReservationVoyage, String> {

    // Méthodes non paginées (pour usage interne ou listes courtes)
    List<ReservationVoyage> findByPassengerId(String passengerId);
    List<ReservationVoyage> findByVoyageId(String voyageId);
    List<ReservationVoyage> findByVoyageIdAndStatus(String voyageId, ReservationVoyageStatus status);
    Optional<ReservationVoyage> findByVoyageIdAndPassengerId(String voyageId, String passengerId);
    boolean existsByVoyageIdAndPassengerId(String voyageId, String passengerId);

    // ✅ Méthodes paginées (utilisées par les contrôleurs)
    Page<ReservationVoyage> findByPassengerId(String passengerId, Pageable pageable);
    Page<ReservationVoyage> findByVoyageId(String voyageId, Pageable pageable);
}