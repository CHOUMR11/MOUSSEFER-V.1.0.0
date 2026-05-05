package com.moussefer.user.service;

import com.moussefer.user.config.MinioProperties;
import com.moussefer.user.config.MinioBucketInitializer;
import com.moussefer.user.entity.*;
import com.moussefer.user.repository.DriverDocumentRepository;
import com.moussefer.user.repository.UserProfileRepository;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverDocumentService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final DriverDocumentRepository documentRepository;
    private final UserProfileRepository userProfileRepository;
    private final MinioBucketInitializer bucketInitializer; // Injection

    private static final Set<String> ALLOWED_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "application/pdf"
    );
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    private static final Set<DocumentType> REQUIRES_EXPIRY = Set.of(
            DocumentType.INSURANCE, DocumentType.TECHNICAL_VISIT
    );

    private static final List<DocumentType> REQUIRED_FOR_VERIFICATION = List.of(
            DocumentType.CIN,
            DocumentType.DRIVING_LICENSE_FRONT,
            DocumentType.DRIVING_LICENSE_BACK,
            DocumentType.VEHICLE_PHOTO,
            DocumentType.INSURANCE,
            DocumentType.TECHNICAL_VISIT,
            DocumentType.LOUAGE_AUTHORIZATION
    );

    @Transactional
    public DriverDocument upload(String userId, DocumentType type, MultipartFile file, LocalDate expiryDate) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds 10MB");
        }
        if (file.getContentType() == null || !ALLOWED_MIME.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Unsupported file type. Allowed: JPEG, PNG, WebP, PDF");
        }
        if (REQUIRES_EXPIRY.contains(type)) {
            if (expiryDate == null) {
                throw new IllegalArgumentException(
                        "Expiry date is required for document type " + type);
            }
            if (expiryDate.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException(
                        "Document expiry date is already in the past — upload a current document");
            }
        }

        // 1. Vérifier que le bucket MinIO est prêt
        if (!bucketInitializer.isBucketReady()) {
            throw new RuntimeException("MinIO bucket not ready");
        }

        String bucketName = minioProperties.getBucket();
        String safeFilename = sanitize(file.getOriginalFilename());
        String objectName = String.format("drivers/%s/%s/%s-%s",
                userId, type.name().toLowerCase(),
                UUID.randomUUID().toString().substring(0, 8),
                safeFilename);

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO upload failed for {}", userId, e);
            throw new RuntimeException("Upload failed: " + e.getMessage());
        }

        String fileUrl = minioProperties.getEndpoint() + "/" + bucketName + "/" + objectName;

        // 2. Supersede previous document
        documentRepository.findCurrentByUserAndType(userId, type).ifPresent(previous -> {
            previous.setSupersededById("pending");
            documentRepository.save(previous);
        });

        // 3. Persist new document
        DriverDocument doc = DriverDocument.builder()
                .userId(userId)
                .documentType(type)
                .fileUrl(fileUrl)
                .fileName(safeFilename)
                .mimeType(file.getContentType())
                .sizeBytes(file.getSize())
                .status(DocumentStatus.PENDING_REVIEW)
                .expiryDate(expiryDate)
                .build();
        doc = documentRepository.save(doc);

        // 4. Update supersededBy link
        String newId = doc.getId();
        documentRepository.findCurrentByUserAndType(userId, type).ifPresent(current -> {
            if (!current.getId().equals(newId)) {
                current.setSupersededById(newId);
                documentRepository.save(current);
            }
        });

        log.info("Driver document uploaded: userId={}, type={}, docId={}", userId, type, doc.getId());
        return doc;
    }

    @Transactional(readOnly = true)
    public List<DriverDocument> myDocuments(String userId) {
        return documentRepository.findCurrentByUser(userId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> kycStatus(String userId) {
        List<DriverDocument> docs = documentRepository.findCurrentByUser(userId);
        Map<DocumentType, DriverDocument> byType = new EnumMap<>(DocumentType.class);
        for (DriverDocument d : docs) byType.put(d.getDocumentType(), d);

        List<String> missing = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        int approvedCount = 0;

        for (DocumentType t : REQUIRED_FOR_VERIFICATION) {
            DriverDocument d = byType.get(t);
            if (d == null) missing.add(t.name());
            else switch (d.getStatus()) {
                case APPROVED        -> approvedCount++;
                case PENDING_REVIEW  -> pending.add(t.name());
                case EXPIRED         -> expired.add(t.name());
                case REJECTED        -> rejected.add(t.name());
            }
        }

        int total = REQUIRED_FOR_VERIFICATION.size();
        int percentage = (approvedCount * 100) / total;
        boolean complete = approvedCount == total;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("complete", complete);
        result.put("percentage", percentage);
        result.put("approvedCount", approvedCount);
        result.put("totalRequired", total);
        result.put("missing", missing);
        result.put("pending", pending);
        result.put("expired", expired);
        result.put("rejected", rejected);
        return result;
    }

    public boolean isKycComplete(String userId) {
        Map<String, Object> status = kycStatus(userId);
        return Boolean.TRUE.equals(status.get("complete"));
    }

    @Transactional
    public Page<DriverDocument> adminListPending(Pageable pageable) {
        return documentRepository.findByStatus(DocumentStatus.PENDING_REVIEW, pageable);
    }

    @Transactional
    public DriverDocument adminApprove(String docId, String adminId) {
        DriverDocument d = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + docId));
        if (d.getStatus() != DocumentStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Document is not pending review (current: " + d.getStatus() + ")");
        }
        d.setStatus(DocumentStatus.APPROVED);
        d.setReviewedBy(adminId);
        d.setReviewedAt(LocalDateTime.now());
        d.setRejectionReason(null);
        documentRepository.save(d);

        Map<String, Object> status = kycStatus(d.getUserId());
        if (Boolean.TRUE.equals(status.get("complete"))) {
            userProfileRepository.findById(d.getUserId()).ifPresent(profile -> {
                profile.setVerificationStatus(VerificationStatus.VERIFIED);
                userProfileRepository.save(profile);
                log.info("Driver {} fully KYC-verified (all documents approved)", d.getUserId());
            });
        }

        log.info("Admin {} approved document {} for driver {}", adminId, docId, d.getUserId());
        return d;
    }

    @Transactional
    public DriverDocument adminReject(String docId, String adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        DriverDocument d = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + docId));
        if (d.getStatus() != DocumentStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Document is not pending review");
        }
        d.setStatus(DocumentStatus.REJECTED);
        d.setReviewedBy(adminId);
        d.setReviewedAt(LocalDateTime.now());
        d.setRejectionReason(reason);
        documentRepository.save(d);
        log.info("Admin {} rejected document {} for driver {}: {}", adminId, docId, d.getUserId(), reason);
        return d;
    }

    public String adminPresignedUrl(String docId) {
        DriverDocument d = documentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + docId));
        String bucket = minioProperties.getBucket();
        String objectName = d.getFileUrl().substring(d.getFileUrl().indexOf(bucket) + bucket.length() + 1);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .method(Method.GET)
                            .expiry(1, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage());
        }
    }

    private String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}