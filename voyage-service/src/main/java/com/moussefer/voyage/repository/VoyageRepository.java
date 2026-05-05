package com.moussefer.voyage.repository;

import com.moussefer.voyage.entity.Voyage;
import com.moussefer.voyage.entity.VoyageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoyageRepository extends JpaRepository<Voyage, String> {

    List<Voyage> findByStatus(VoyageStatus status);

    // ✅ NEW: Paginated listing for public endpoint (FIXED)
    Page<Voyage> findByStatusOrderByDepartureDateAsc(VoyageStatus status, Pageable pageable);

    // Recherche simple (utilisée par l'ancienne version de searchVoyages)
    Page<Voyage> findByDepartureCityAndArrivalCityAndDepartureDateBetweenAndStatus(
            String dep, String arr, LocalDateTime from, LocalDateTime to, VoyageStatus status, Pageable pageable);

    // ✅ Recherche avancée avec filtres optionnels (prix, organisateur)
    @Query("""
        SELECT v FROM Voyage v
        WHERE v.departureCity = :dep 
          AND v.arrivalCity = :arr
          AND v.departureDate BETWEEN :from AND :to
          AND v.status = :status
          AND (:organizerId IS NULL OR v.organizerId = :organizerId)
          AND (:minPrice IS NULL OR v.pricePerSeat >= :minPrice)
          AND (:maxPrice IS NULL OR v.pricePerSeat <= :maxPrice)
    """)
    Page<Voyage> searchVoyages(@Param("dep") String dep,
                               @Param("arr") String arr,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("status") VoyageStatus status,
                               @Param("organizerId") String organizerId,
                               @Param("minPrice") Double minPrice,
                               @Param("maxPrice") Double maxPrice,
                               Pageable pageable);

    // ✅ Liste paginée des voyages d'un organisateur
    Page<Voyage> findByOrganizerId(String organizerId, Pageable pageable);

    // ✅ Liste non paginée (utilisée pour certaines opérations internes)
    List<Voyage> findByOrganizerId(String organizerId);

    // ✅ Verrou pessimiste pour la réservation (évite les conflits de places)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voyage v WHERE v.id = :id")
    Optional<Voyage> findByIdWithLock(@Param("id") String id);

    // ✅ Décrémentation atomique des places disponibles
    @Modifying
    @Query("UPDATE Voyage v SET v.availableSeats = v.availableSeats - :seats WHERE v.id = :id AND v.availableSeats >= :seats")
    int decrementSeats(@Param("id") String id, @Param("seats") int seats);
}