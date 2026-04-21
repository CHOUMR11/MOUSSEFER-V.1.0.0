package com.moussefer.payment.repository;

import com.moussefer.payment.entity.Payment;
import com.moussefer.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String>,
        JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByReservationId(String reservationId);
    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);
    Page<Payment> findByPassengerIdOrderByCreatedAtDesc(String passengerId, Pageable pageable);
    List<Payment> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    List<Payment> findByStatusAndCreatedAtBetween(PaymentStatus status, LocalDateTime from, LocalDateTime to);

    @Query("SELECT p.driverId, SUM(p.driverPayout), COUNT(p) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.driverPayout IS NOT NULL GROUP BY p.driverId")
    List<Object[]> sumDriverPayouts();

    @Query("SELECT COALESCE(SUM(p.platformCommission), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.createdAt BETWEEN :from AND :to")
    BigDecimal sumCommissionsInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCEEDED' AND p.createdAt BETWEEN :from AND :to")
    BigDecimal sumRevenueInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'REFUNDED' AND p.createdAt BETWEEN :from AND :to")
    BigDecimal sumRefundsInPeriod(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    long countByStatus(PaymentStatus status);
}
