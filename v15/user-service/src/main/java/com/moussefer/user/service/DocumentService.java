package com.moussefer.user.service;

import com.moussefer.user.config.MinioProperties;
import com.moussefer.user.entity.UserProfile;
import com.moussefer.user.repository.UserProfileRepository;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final UserProfileRepository userProfileRepository;

    /**
     * Upload un document (KYC) pour un utilisateur.
     * Stocke le fichier dans MinIO et enregistre l'URL permanente dans le profil utilisateur.
     */
    public String uploadDocument(String userId, String documentType, MultipartFile file) {
        try {
            String bucketName = minioProperties.getBucket();
            ensureBucketExists(bucketName);

            String safeFilename = sanitizeFilename(file.getOriginalFilename());
            String objectName = buildObjectName(userId, documentType, safeFilename);

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            String fileUrl = minioProperties.getEndpoint() + "/" + bucketName + "/" + objectName;

            UserProfile profile = userProfileRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            profile.addDocumentUrl(documentType, fileUrl);
            userProfileRepository.save(profile);

            log.info("Document uploaded for user {}: {}", userId, fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload document for user {}", userId, e);
            throw new RuntimeException("Document upload failed: " + e.getMessage());
        }
    }

    /**
     * Génère une URL temporaire (presigned) pour accéder à un document.
     * Utilisé par l'admin pour consulter les pièces justificatives sans exposer l'URL permanente.
     */
    public String generatePresignedUrl(String userId, String documentType) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String documentUrl = profile.getDocumentUrls().get(documentType);
        if (documentUrl == null) {
            throw new RuntimeException("Document not found for type: " + documentType);
        }

        String bucketName = minioProperties.getBucket();
        String objectName = extractObjectName(documentUrl, bucketName);

        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(5, TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for user {} document {}", userId, documentType, e);
            throw new RuntimeException("Unable to generate document access link");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────
    private void ensureBucketExists(String bucketName) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            log.info("Bucket created: {}", bucketName);
        }
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "document";
        }
        // Garde uniquement lettres, chiffres, point, tiret, underscore
        return originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String buildObjectName(String userId, String documentType, String safeFilename) {
        return userId + "/" + documentType + "/" + UUID.randomUUID() + "_" + safeFilename;
    }

    private String extractObjectName(String fullUrl, String bucketName) {
        // L'URL est de la forme : http://minio:9000/bucketName/chemin/objet
        String marker = "/" + bucketName + "/";
        int index = fullUrl.indexOf(marker);
        if (index == -1) {
            throw new RuntimeException("Invalid document URL format");
        }
        return fullUrl.substring(index + marker.length());
    }
}