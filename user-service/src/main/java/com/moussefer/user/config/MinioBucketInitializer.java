package com.moussefer.user.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioBucketInitializer {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private volatile boolean bucketReady = false;

    @PostConstruct
    public void initBucket() {
        String bucket = minioProperties.getBucket();
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            } else {
                log.info("MinIO bucket already exists: {}", bucket);
            }
            bucketReady = true;
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket: {}", e.getMessage());
        }
    }

    public boolean isBucketReady() {
        return bucketReady;
    }
}