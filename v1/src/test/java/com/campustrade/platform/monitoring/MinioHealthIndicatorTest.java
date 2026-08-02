package com.campustrade.platform.monitoring;

import com.campustrade.platform.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioHealthIndicatorTest {

    @Mock
    private MinioClient minioClient;

    private MinioHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getMinio().setBucket("campus-trade");
        healthIndicator = new MinioHealthIndicator(minioClient, properties);
    }

    @Test
    void reportsUpWhenConfiguredBucketExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenConfiguredBucketDoesNotExist() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        assertThat(healthIndicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWithoutLeakingExceptionDetails() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new IllegalStateException("sensitive connection details"));

        var health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }
}
