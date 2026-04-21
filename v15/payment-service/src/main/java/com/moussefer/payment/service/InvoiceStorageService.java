package com.moussefer.payment.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@Slf4j
public class InvoiceStorageService {

    private final MinioClient minioClient;
    private final boolean minioEnabled;
    private final String bucket;
    private final String endpoint;

    public InvoiceStorageService(
            @Value("${minio.endpoint:}") String endpoint,
            @Value("${minio.access-key:}") String accessKey,
            @Value("${minio.secret-key:}") String secretKey,
            @Value("${minio.bucket:invoices}") String bucket,
            @Value("${minio.enabled:false}") boolean minioEnabled) {
        this.minioEnabled = minioEnabled;
        this.bucket = bucket;
        this.endpoint = endpoint;
        if (minioEnabled && !endpoint.isBlank() && !accessKey.isBlank() && !secretKey.isBlank()) {
            this.minioClient = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
            log.info("MinIO client initialized for bucket: {}", bucket);
        } else {
            this.minioClient = null;
            if (minioEnabled) {
                log.warn("MinIO is enabled but configuration is incomplete. Endpoint, accessKey or secretKey missing.");
            } else {
                log.info("MinIO storage is disabled. Invoices will not be persisted.");
            }
        }
    }

    public String uploadIfEnabled(String objectName, byte[] data) {
        if (!minioEnabled || minioClient == null) {
            log.debug("MinIO upload skipped (disabled or client not available) for object: {}", objectName);
            return null;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' created successfully", bucket);
            }
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType("application/pdf")
                    .build());
            // Construire une URL accessible (selon configuration MinIO)
            String fileUrl = String.format("%s/%s/%s", endpoint.replaceAll("/$", ""), bucket, objectName);
            log.info("Invoice uploaded to MinIO: {}", fileUrl);
            return fileUrl;
        } catch (Exception e) {
            log.error("Failed to upload invoice '{}' to MinIO", objectName, e);
            return null;
        }
    }
}