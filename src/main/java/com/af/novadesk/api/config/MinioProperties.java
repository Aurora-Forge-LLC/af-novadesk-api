package com.af.novadesk.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MinIO / S3-compatible object storage.
 * <p>
 * Binds to the {@code app.minio.*} prefix in application YAML files.
 *
 * @param url        MinIO server endpoint (e.g. {@code http://localhost:9000})
 * @param accessKey  MinIO root / access key
 * @param secretKey  MinIO secret key
 * @param bucket     Default bucket name
 * @param region     S3 region (default: {@code us-east-1})
 */
@ConfigurationProperties(prefix = "app.minio")
public record MinioProperties(
    String url,
    String accessKey,
    String secretKey,
    String bucket,
    String region
) {}
