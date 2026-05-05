package com.moussefer.voyage.service;

import com.moussefer.voyage.entity.Voyage;
import com.moussefer.voyage.exception.BusinessException;
import com.moussefer.voyage.exception.ResourceNotFoundException;
import com.moussefer.voyage.repository.VoyageRepository;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

/**
 * Cover image upload for voyages organisés.
 *
 * The frontend (voyage.service.ts) calls
 *     POST /api/v1/voyages/{id}/image   (multipart, field "file")
 * to attach a cover image to a voyage. This service validates the file,
 * uploads it to MinIO, and persists the resulting URL on the Voyage row.
 *
 * Only the organizer who owns the voyage can upload its image — checked
 * by VoyageController via the X-User-Id and X-User-Role headers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoyageImageService {

    private final MinioClient minioClient;
    private final VoyageRepository voyageRepository;

    @Value("${minio.endpoint}")
    private String minioEndpoint;

    @Value("${minio.voyage-images-bucket}")
    private String voyageImagesBucket;

    private static final Set<String> ALLOWED_IMAGE_MIME = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp"
    );
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024; // 10 MB

    /**
     * Uploads the image, persists the URL on the Voyage, returns the URL.
     *
     * @param organizerId  caller's userId (must own the voyage)
     * @param voyageId     target voyage
     * @param file         the multipart image file
     * @return the public URL of the uploaded image
     */
    @Transactional
    public String uploadVoyageImage(String organizerId, String voyageId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File is required");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BusinessException("Image must be 10MB or smaller");
        }
        if (file.getContentType() == null || !ALLOWED_IMAGE_MIME.contains(file.getContentType())) {
            throw new BusinessException("Unsupported image type. Allowed: JPEG, PNG, WebP");
        }

        Voyage voyage = voyageRepository.findById(voyageId)
                .orElseThrow(() -> new ResourceNotFoundException("Voyage not found: " + voyageId));
        if (!organizerId.equals(voyage.getOrganizerId())) {
            throw new BusinessException("You can only upload images to your own voyages");
        }

        ensureBucket(voyageImagesBucket);
        String safeName = sanitize(file.getOriginalFilename());
        String objectName = String.format("%s/%s-%s",
                voyageId, UUID.randomUUID().toString().substring(0, 8), safeName);

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(voyageImagesBucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
        } catch (Exception e) {
            log.error("MinIO upload failed for voyage {} image", voyageId, e);
            throw new BusinessException("Image upload failed: " + e.getMessage());
        }

        String fileUrl = minioEndpoint + "/" + voyageImagesBucket + "/" + objectName;
        voyage.setImageUrl(fileUrl);
        voyageRepository.save(voyage);

        log.info("Image uploaded for voyage {}: {}", voyageId, objectName);
        return fileUrl;
    }

    private void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new BusinessException("Bucket check failed: " + e.getMessage());
        }
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) return "image";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
