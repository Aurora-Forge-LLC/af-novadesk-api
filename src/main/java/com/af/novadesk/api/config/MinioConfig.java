package com.af.novadesk.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Spring configuration that creates an {@link S3Client} bean connected to a
 * MinIO (or any S3-compatible) object store.
 * <p>
 * On startup, the default bucket is created automatically if it does not exist.
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    private final MinioProperties props;

    public MinioConfig(final MinioProperties props) {
        this.props = props;
    }

    @Bean
    public S3Client s3Client(final MinioProperties props) {
        log.info("Initializing S3Client for MinIO at {}", props.url());

        return S3Client.builder()
            .endpointOverride(URI.create(props.url()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())
                )
            )
            .region(Region.of(props.region()))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // required for MinIO (non-AWS endpoints)
                .build())
            .build();
    }

    /**
     * Creates an {@link S3Presigner} bean for generating short-lived pre-signed
     * download URLs (e.g. for expense attachments). Uses the same endpoint,
     * credentials, and region as {@link #s3Client(MinioProperties)}.
     *
     * <p>{@code pathStyleAccessEnabled} must be {@code true} for MinIO — virtual-hosted
     * style URLs are only valid for real AWS S3 endpoints.</p>
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
            .endpointOverride(URI.create(props.url()))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())
                )
            )
            .region(Region.of(props.region()))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }

    @Bean
    public String minioBucketName(final MinioProperties props) {
        return props.bucket();
    }

    /**
     * Ensures the configured bucket exists on application startup.
     * Uses method injection so {@link S3Client} is resolved by the bean factory
     * after it has been fully created, avoiding a circular constructor dependency.
     * Creates the bucket if a {@code 404 Not Found} is returned by the head-bucket call.
     */
    @Bean(initMethod = "init")
    public MinioBucketInitializer minioBucketInitializer(final S3Client s3Client, final MinioProperties props) {
        return new MinioBucketInitializer(s3Client, props);
    }

    /**
     * Thin helper whose {@link #init()} method is invoked by the container after
     * both {@link S3Client} and {@link MinioProperties} beans are fully available.
     */
    static class MinioBucketInitializer {

        private final S3Client s3Client;
        private final MinioProperties props;

        MinioBucketInitializer(final S3Client s3Client, final MinioProperties props) {
            this.s3Client = s3Client;
            this.props = props;
        }

        @SuppressWarnings("unused")
        public void init() {
            final String bucket = props.bucket();
            try {
                s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucket)
                    .build());
                log.info("MinIO bucket '{}' already exists", bucket);
            } catch (final S3Exception e) {
                if (e.statusCode() == 404) {
                    log.info("MinIO bucket '{}' not found — creating it now", bucket);
                    s3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(bucket)
                        .build());
                    log.info("MinIO bucket '{}' created successfully", bucket);
                } else {
                    log.warn("Could not verify MinIO bucket '{}': {}", bucket, e.getMessage());
                }
            } catch (final SdkClientException e) {
                log.warn("Could not connect to MinIO at startup — service may be unavailable: {}", e.getMessage());
            }
        }
    }
}
