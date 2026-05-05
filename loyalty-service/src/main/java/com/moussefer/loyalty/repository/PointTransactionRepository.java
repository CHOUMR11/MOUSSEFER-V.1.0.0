package com.moussefer.loyalty.repository;

import com.moussefer.loyalty.entity.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, String> {
    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
