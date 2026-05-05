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

    // ─── V21: Organizer dashboard queries ───────────────────────

    /**
     * All reservations for voyages belonging to an organizer.
     * Joins voyage.organizerId — this is the main feed for the
     * organizer's "Réservation" view.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT r FROM ReservationVoyage r
        WHERE r.voyageId IN (
            SELECT v.id FROM Voyage v WHERE v.organizerId = :organizerId
        )
        ORDER BY r.createdAt DESC
    """)
    Page<ReservationVoyage> findByOrganizer(@org.springframework.data.repository.query.Param("organizerId") String organizerId,
                                             Pageable pageable);

    /**
     * Filter by booking source for the organizer's Hors Moussefer tab.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT r FROM ReservationVoyage r
        WHERE r.voyageId IN (
            SELECT v.id FROM Voyage v WHERE v.organizerId = :organizerId
        )
        AND r.bookingSource = :source
        ORDER BY r.createdAt DESC
    """)
    Page<ReservationVoyage> findByOrganizerAndSource(@org.springframework.data.repository.query.Param("organizerId") String organizerId,
                                                      @org.springframework.data.repository.query.Param("source") com.moussefer.voyage.entity.BookingSource source,
                                                      Pageable pageable);

    /**
     * All reservations on an organizer's voyages within a date range —
     * used by the finances & statistics endpoints.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT r FROM ReservationVoyage r
        WHERE r.voyageId IN (
            SELECT v.id FROM Voyage v WHERE v.organizerId = :organizerId
        )
        AND r.createdAt BETWEEN :from AND :to
    """)
    List<ReservationVoyage> findByOrganizerInPeriod(@org.springframework.data.repository.query.Param("organizerId") String organizerId,
                                                     @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
                                                     @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

    /**
     * Sum seats held by non-cancelled reservations (PENDING_ORGANIZER, PENDING_PAYMENT, CONFIRMED).
     * Used to prevent overbooking: effective available = availableSeats - pendingSeats.
     */
    @org.springframework.data.jpa.repository.Query("""
        SELECT COALESCE(SUM(r.seatsReserved), 0) FROM ReservationVoyage r
        WHERE r.voyageId = :voyageId
          AND r.status IN ('PENDING_ORGANIZER', 'PENDING_PAYMENT', 'CONFIRMED')
    """)
    int sumPendingAndConfirmedSeats(@org.springframework.data.repository.query.Param("voyageId") String voyageId);
}