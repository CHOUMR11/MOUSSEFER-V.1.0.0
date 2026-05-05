package com.moussefer.trajet.repository;

import com.moussefer.trajet.entity.Trajet;
import com.moussefer.trajet.entity.TrajetStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TrajetRepository extends JpaRepository<Trajet, String>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<Trajet> {

    // ─── Recherche de trajets disponibles ────────────────────────────────
    @Query("""
        SELECT t FROM Trajet t
        WHERE t.departureCity = :dep AND t.arrivalCity = :arr
          AND t.departureDate BETWEEN :from AND :to
          AND t.status NOT IN ('CANCELLED','DEPARTED')
        ORDER BY t.priorityOrder ASC, t.createdAt ASC
    """)
    List<Trajet> findByRouteAndDate(@Param("dep") String dep,
                                    @Param("arr") String arr,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    // ─── File d'attente pour promotion (LOCKED ou FULL) ─────────────────
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t FROM Trajet t
        WHERE t.departureCity = :dep AND t.arrivalCity = :arr
          AND t.departureDate BETWEEN :from AND :to
          AND t.status IN ('LOCKED', 'FULL')
        ORDER BY t.priorityOrder ASC
    """)
    List<Trajet> findNextInQueue(@Param("dep") String dep,
                                 @Param("arr") String arr,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);

    // ─── Verrouillage pour calcul de priorité ───────────────────────────
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT t FROM Trajet t
        WHERE t.departureCity = :dep AND t.arrivalCity = :arr
          AND t.departureDate BETWEEN :from AND :to
        ORDER BY t.priorityOrder DESC
    """)
    List<Trajet> lockRouteForDate(@Param("dep") String dep,
                                  @Param("arr") String arr,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    // ─── Trajets d'un chauffeur ─────────────────────────────────────────
    List<Trajet> findByDriverIdOrderByCreatedAtDesc(String driverId);
    List<Trajet> findByDriverIdAndStatusNot(String driverId, TrajetStatus status);
    int countByDepartureCityAndArrivalCityAndDepartureDateBetween(
            String dep, String arr, LocalDateTime from, LocalDateTime to);

    // ─── Gestion atomique des places (legacy) ───────────────────────────
    @Modifying
    @Query("UPDATE Trajet t SET t.availableSeats = t.availableSeats - :seats, t.version = t.version + 1 " +
            "WHERE t.id = :id AND t.availableSeats >= :seats AND t.version = :version")
    int decrementSeatsWithVersion(@Param("id") String id,
                                  @Param("seats") int seats,
                                  @Param("version") Long version);

    @Modifying
    @Query("UPDATE Trajet t SET t.availableSeats = t.availableSeats + :seats, t.version = t.version + 1 " +
            "WHERE t.id = :id")
    void incrementSeats(@Param("id") String id, @Param("seats") int seats);

    // ─── NOUVEAU : Réservations temporaires (anti‑race condition) ───────
    /**
     * Réserve temporairement des places (incrémente reservedSeats).
     * Vérifie que totalSeats - reservedSeats - availableSeats >= seats demandés.
     */
    // FIX #1: was (totalSeats-reservedSeats-availableSeats) which is always 0 on fresh trajet
    // CORRECT: net free slots = availableSeats - reservedSeats
    @Modifying
    @Query("UPDATE Trajet t SET t.reservedSeats = t.reservedSeats + :seats " +
            "WHERE t.id = :id AND (t.availableSeats - t.reservedSeats) >= :seats")
    int reserveSeats(@Param("id") String id, @Param("seats") int seats);

    /**
     * Confirme une réservation temporaire : décrémente à la fois reservedSeats et availableSeats.
     */
    @Modifying
    @Query("UPDATE Trajet t SET t.reservedSeats = t.reservedSeats - :seats, " +
            "t.availableSeats = t.availableSeats - :seats " +
            "WHERE t.id = :id AND t.reservedSeats >= :seats AND t.availableSeats >= :seats")
    int confirmSeats(@Param("id") String id, @Param("seats") int seats);

    /**
     * Libère une réservation temporaire (décrémente reservedSeats uniquement).
     */
    @Modifying
    @Query("UPDATE Trajet t SET t.reservedSeats = t.reservedSeats - :seats " +
            "WHERE t.id = :id AND t.reservedSeats >= :seats")
    int releaseReservedSeats(@Param("id") String id, @Param("seats") int seats);

    /**
     * Driver — corriger le compteur de places disponibles.
     *
     * Permet au chauffeur de fixer availableSeats à une valeur précise pour
     * refléter la réalité du véhicule (passager monté/descendu hors-plateforme,
     * place bloquée pour bagage, etc.).
     *
     * La condition WHERE ... BETWEEN 0 AND (totalSeats - reservedSeats)
     * empêche atomiquement toute valeur qui écraserait des réservations
     * confirmées. La couche service double-check côté Java pour fournir
     * un message d'erreur explicite si la valeur est hors bornes.
     */
    @Modifying
    @Query("UPDATE Trajet t SET t.availableSeats = :newAvailable, " +
            "t.version = t.version + 1 " +
            "WHERE t.id = :id AND :newAvailable BETWEEN 0 AND (t.totalSeats - t.reservedSeats)")
    int setAvailableSeats(@Param("id") String id, @Param("newAvailable") int newAvailable);
}