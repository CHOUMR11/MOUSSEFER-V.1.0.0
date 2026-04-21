package com.moussefer.reservation.repository;

import com.moussefer.reservation.entity.Dispute;
import com.moussefer.reservation.entity.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, String> {
    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);
    Page<Dispute> findByReporterId(String reporterId, Pageable pageable);
    List<Dispute> findByReservationId(String reservationId);
    long countByStatus(DisputeStatus status);
}
