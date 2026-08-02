package com.campustrade.platform.monitoring;

import com.campustrade.platform.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnEnabledHealthIndicator("minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioHealthIndicator(MinioClient minioClient, AppProperties appProperties) {
        this.minioClient = minioClient;
        this.bucket = appProperties.getMinio().getBucket();
    }

    @Override
    public Health health() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );
            return bucketExists ? Health.up().build() : Health.down().build();
        } catch (Exception exception) {
            return Health.down().build();
        }
    }
}
