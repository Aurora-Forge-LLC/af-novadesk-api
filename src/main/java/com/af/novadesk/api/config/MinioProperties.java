package com.af.novadesk.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for MinIO / S3-compatible object storage.
 * <p>
 * Binds to the {@code app.minio.*} prefix in application YAML files.
 *
 * @param url        MinIO server endpoint (e.g. {@code http://localhost:9000})
 * @param accessKey  MinIO root / access key
 * @param secretKey  MinIO secret key (masked in {@code /actuator/env})
 * @param bucket     Default bucket name
 * @param region     S3 region (default: {@code us-east-1})
 */
@Validated
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
    @NotBlank String url,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @NotBlank String bucket,
    @NotBlank String region
) {}
