package com.moussefer.payment.repository;

import com.moussefer.payment.entity.PromoCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PromoCodeRepository extends JpaRepository<PromoCode, String> {

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Page<PromoCode> findAll(Pageable pageable);

    Page<PromoCode> findByActive(boolean active, Pageable pageable);

    // Active = flag true + not expired + not exhausted
    @Query("""
        SELECT p FROM PromoCode p
        WHERE p.active = true
          AND (p.validUntil IS NULL OR p.validUntil > :now)
          AND (p.maxUses IS NULL OR p.usedCount < p.maxUses)
        """)
    Page<PromoCode> findCurrentlyValid(@Param("now") LocalDateTime now, Pageable pageable);

    // Expired = past validUntil OR maxUses reached
    @Query("""
        SELECT p FROM PromoCode p
        WHERE p.active = false
           OR (p.validUntil IS NOT NULL AND p.validUntil <= :now)
           OR (p.maxUses IS NOT NULL AND p.usedCount >= p.maxUses)
        """)
    Page<PromoCode> findExpiredOrExhausted(@Param("now") LocalDateTime now, Pageable pageable);

    // Stats counts
    long countByActive(boolean active);

    @Query("SELECT COUNT(p) FROM PromoCode p WHERE p.validUntil IS NOT NULL AND p.validUntil <= :now")
    long countExpired(@Param("now") LocalDateTime now);

    @Query("SELECT COUNT(p) FROM PromoCode p WHERE p.maxUses IS NOT NULL AND p.usedCount >= p.maxUses")
    long countExhausted();

    @Query("SELECT COALESCE(SUM(p.usedCount), 0) FROM PromoCode p")
    long sumTotalUsages();

    @Modifying
    @Query("""
        UPDATE PromoCode p
        SET p.usedCount = p.usedCount + 1
        WHERE p.id = :id
          AND (p.maxUses IS NULL OR p.usedCount < p.maxUses)
        """)
    int atomicIncrementUsedCount(@Param("id") String id);
}
