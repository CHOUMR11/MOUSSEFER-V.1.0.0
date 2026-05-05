package com.moussefer.user.repository;

import com.moussefer.user.entity.DocumentStatus;
import com.moussefer.user.entity.DocumentType;
import com.moussefer.user.entity.DriverDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, String> {

    /**
     * Latest document of a given type for a user (not superseded).
     */
    @Query("""
        SELECT d FROM DriverDocument d
        WHERE d.userId = :userId
          AND d.documentType = :type
          AND d.supersededById IS NULL
    """)
    Optional<DriverDocument> findCurrentByUserAndType(@Param("userId") String userId,
                                                      @Param("type") DocumentType type);

    /**
     * All documents currently in use for a user (not superseded).
     * Used by the frontend to render the KYC dossier state.
     */
    @Query("""
        SELECT d FROM DriverDocument d
        WHERE d.userId = :userId
          AND d.supersededById IS NULL
        ORDER BY d.documentType
    """)
    List<DriverDocument> findCurrentByUser(@Param("userId") String userId);

    /**
     * Admin listing — all documents pending review, paginated.
     * Used by the MODERATOR/SUPER_ADMIN "KYC queue" view.
     */
    Page<DriverDocument> findByStatus(DocumentStatus status, Pageable pageable);

    Page<DriverDocument> findByUserIdAndStatus(String userId, DocumentStatus status, Pageable pageable);

    /**
     * Scheduler query: documents that have passed their expiry date
     * but still carry status APPROVED. They need to be flagged EXPIRED.
     */
    @Query("""
        SELECT d FROM DriverDocument d
        WHERE d.status = 'APPROVED'
          AND d.expiryDate IS NOT NULL
          AND d.expiryDate < :today
    """)
    List<DriverDocument> findExpiredApproved(@Param("today") LocalDate today);

    /**
     * Scheduler mass-update: flip all expired approved docs to EXPIRED.
     * Run by DocumentExpiryScheduler nightly.
     */
    @Modifying
    @Query("""
        UPDATE DriverDocument d
        SET d.status = 'EXPIRED'
        WHERE d.status = 'APPROVED'
          AND d.expiryDate IS NOT NULL
          AND d.expiryDate < :today
    """)
    int markExpired(@Param("today") LocalDate today);

    /**
     * Scheduler query: retrieve distinct user IDs whose documents have expired.
     * Used to recalc verification status globally.
     */
    @Query("""
        SELECT DISTINCT d.userId
        FROM DriverDocument d
        WHERE d.status = 'EXPIRED'
    """)
    List<String> findDistinctUserIdsWithExpiredDocuments();
}