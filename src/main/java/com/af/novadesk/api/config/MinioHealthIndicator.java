package com.af.novadesk.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Custom Actuator health indicator that verifies MinIO / S3 connectivity
 * by sending a {@code HeadBucket} request to the configured bucket.
 * <p>
 * Status will appear at {@code GET /novadesk-api/actuator/health} under the
 * {@code minio} key.
 */
@Component
public class MinioHealthIndicator extends AbstractHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(MinioHealthIndicator.class);

    private final S3Client s3Client;
    private final String bucketName;

    public MinioHealthIndicator(final S3Client s3Client, final String minioBucketName) {
        this.s3Client = s3Client;
        this.bucketName = minioBucketName;
    }

    @Override
    protected void doHealthCheck(final Health.Builder builder) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                .bucket(bucketName)
                .build());

            builder.up()
                .withDetail("bucket", bucketName)
                .withDetail("endpoint", s3Client.serviceClientConfiguration().endpointOverride().toString());

        } catch (final S3Exception e) {
            log.warn("MinIO health check failed: {}", e.getMessage());
            builder.down(e)
                .withDetail("bucket", bucketName)
                .withDetail("error", e.getMessage())
                .withDetail("statusCode", e.statusCode());

        } catch (final Exception e) {
            log.warn("MinIO health check failed: {}", e.getMessage());
            builder.down(e)
                .withDetail("bucket", bucketName)
                .withDetail("error", e.getMessage());
        }
    }
}
