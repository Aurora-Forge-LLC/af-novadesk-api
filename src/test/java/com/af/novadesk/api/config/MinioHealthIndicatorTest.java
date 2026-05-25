package com.af.novadesk.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ServiceClientConfiguration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioHealthIndicator")
class MinioHealthIndicatorTest {

    private static final String BUCKET = "test-bucket";
    private static final String ENDPOINT = "http://localhost:9000";

    @Mock
    private S3Client s3Client;

    @Mock
    private MinioProperties minioProperties;

    private MinioHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        when(minioProperties.bucket()).thenReturn(BUCKET);
        indicator = new MinioHealthIndicator(s3Client, minioProperties);
    }

    @Test
    @DisplayName("reports UP when headBucket succeeds")
    void upWhenBucketReachable() {
        final S3ServiceClientConfiguration config = mock(S3ServiceClientConfiguration.class);
        when(config.endpointOverride()).thenReturn(Optional.of(URI.create(ENDPOINT)));
        when(s3Client.serviceClientConfiguration()).thenReturn(config);

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(null);

        final Health health = healthResult();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
            .containsEntry("bucket", BUCKET)
            .containsEntry("endpoint", ENDPOINT);
    }

    @Test
    @DisplayName("reports DOWN when S3Exception is thrown")
    void downOnS3Exception() {
        final S3Exception exception = mock(S3Exception.class);
        when(exception.statusCode()).thenReturn(500);
        when(exception.getMessage()).thenReturn("Internal error");

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(exception);

        final Health health = healthResult();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
            .containsEntry("bucket", BUCKET)
            .containsEntry("error", "Internal error")
            .containsEntry("statusCode", 500);
    }

    @Test
    @DisplayName("reports DOWN when a generic Exception is thrown")
    void downOnGenericException() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(new RuntimeException("Network timeout"));

        final Health health = healthResult();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
            .containsEntry("bucket", BUCKET)
            .containsEntry("error", "Network timeout");
    }

    // --------------- helpers ---------------

    /**
     * Invokes {@link MinioHealthIndicator#doHealthCheck(Health.Builder)} and
     * captures the resulting {@link Health} object from the builder.
     */
    private Health healthResult() {
        final Health.Builder builder = new Health.Builder();
        indicator.doHealthCheck(builder);
        return builder.build();
    }
}
