package com.moussefer.reservation.repository;

import com.moussefer.reservation.entity.Reservation;
import com.moussefer.reservation.entity.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String>,
        JpaSpecificationExecutor<Reservation> {

    // ─── User queries ────────────────────────────────────────────────────
    Page<Reservation> findByPassengerId(String passengerId, Pageable pageable);
    List<Reservation> findByDriverId(String driverId);
    Page<Reservation> findByDriverIdAndStatus(String driverId, ReservationStatus status, Pageable pageable);
    List<Reservation> findByTrajetId(String trajetId);

    /**
     * V22 — Scenario precondition enforcement: a passenger cannot hold more
     * than one active reservation on the same trajet. "Active" means not in
     * a terminal state (REFUSED, CANCELLED, ESCALATED).
     *
     * Used in ReservationService.create() to block duplicates.
     */
    boolean existsByPassengerIdAndTrajetIdAndStatusIn(String passengerId,
                                                       String trajetId,
                                                       List<ReservationStatus> statuses);

    // ─── Admin filters ───────────────────────────────────────────────────
    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);
    Page<Reservation> findByPassengerIdAndStatus(String passengerId, ReservationStatus status, Pageable pageable);
    Page<Reservation> findByDriverIdAndPassengerId(String driverId, String passengerId, Pageable pageable);

    // ─── Scheduler : driver reminder (5 min) ────────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.status = 'PENDING_DRIVER'
          AND r.reminderSent = false
          AND r.createdAt <= :fiveMinAgo
    """)
    List<Reservation> findPendingForReminder(LocalDateTime fiveMinAgo);

    // ─── Scheduler : admin notification (8 min) ──────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.status = 'PENDING_DRIVER'
          AND r.adminNotified = false
          AND r.createdAt <= :eightMinAgo
    """)
    List<Reservation> findPendingForAdminNotification(LocalDateTime eightMinAgo);

    // ─── Scheduler : auto-escalation (15 min) ────────────────────────────
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.status = 'PENDING_DRIVER'
          AND r.escalated = false
          AND r.driverResponseDeadline <= :now
    """)
    List<Reservation> findExpiredPending(LocalDateTime now);

    // ─── Check for avis-service ──────────────────────────────────────────
    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM Reservation r
        WHERE r.passengerId = :passengerId
          AND r.driverId = :driverId
          AND r.trajetId = :trajetId
          AND r.status = 'CONFIRMED'
    """)
    boolean existsConfirmedReservation(
            @Param("passengerId") String passengerId,
            @Param("driverId") String driverId,
            @Param("trajetId") String trajetId);

    // ─── Dashboard stats ─────────────────────────────────────────────────
    long countByStatus(ReservationStatus status);
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    long countByTrajetIdAndStatusIn(String trajetId, List<ReservationStatus> statuses);

    // ─── SEC-01: Check passenger has confirmed/paid reservation with driver ──
    @Query("""
        SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
        FROM Reservation r
        WHERE r.passengerId = :passengerId
          AND r.driverId = :driverId
          AND r.status IN ('CONFIRMED', 'PAID')
    """)
    boolean hasConfirmedPaymentForDriver(
            @Param("passengerId") String passengerId,
            @Param("driverId") String driverId);

}
