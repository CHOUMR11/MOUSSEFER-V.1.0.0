package com.moussefer.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single document uploaded by a driver.
 *
 * The storage strategy is a URL (MinIO / S3 signed URL) — we don't keep
 * the binary in the database. For documents with legal expiry dates
 * (insurance, technical visit), we store the expiry in expiryDate and
 * a scheduler flips the status to EXPIRED when the date passes.
 *
 * A driver has one APPROVED row per type at a time. When they re-upload
 * (for a renewal or after rejection), the previous row is kept for
 * audit but superseded by the new one.
 */
@Entity
@Table(name = "driver_documents",
       indexes = {
               @Index(name = "idx_driver_doc_user",   columnList = "user_id"),
               @Index(name = "idx_driver_doc_status", columnList = "status"),
               @Index(name = "idx_driver_doc_type",   columnList = "document_type")
       })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DriverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false, length = 40)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentType documentType;

    /**
     * Object storage URL (MinIO signed URL or S3).
     */
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING_REVIEW;

    /**
     * For documents with legal expiry dates (insurance, technical visit).
     * Null for documents without expiry (CIN, driving license).
     */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /**
     * Admin ID who reviewed the document. Null while PENDING_REVIEW.
     */
    @Column(name = "reviewed_by", length = 40)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * If status = REJECTED, the reason is stored here and shown to the driver
     * so they know why and what to fix.
     */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * When a driver re-uploads a document of the same type, the previous
     * document's supersededById is set to the new one for audit trail.
     */
    @Column(name = "superseded_by_id", length = 40)
    private String supersededById;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
