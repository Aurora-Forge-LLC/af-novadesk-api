package com.af.novadesk.api.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MinioConfig.MinioBucketInitializer#init()}.
 * <p>
 * The initializer is a package-private static inner class, so we instantiate it
 * directly with a mock {@link S3Client} and {@link MinioProperties}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MinioBucketInitializer")
class MinioConfigTest {

    private static final String BUCKET = "test-bucket";

    @Mock
    private S3Client s3Client;

    @Mock
    private MinioProperties minioProperties;

    private MinioConfig.MinioBucketInitializer initializer;

    @BeforeEach
    void setUp() {
        when(minioProperties.bucket()).thenReturn(BUCKET);
        initializer = new MinioConfig.MinioBucketInitializer(s3Client, minioProperties);
    }

    @Test
    @DisplayName("does nothing when bucket already exists")
    void bucketAlreadyExists() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(null);

        initializer.init();

        verify(s3Client, times(1)).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("creates bucket when headBucket returns 404")
    void createsBucketOn404() {
        final S3Exception notFound = mock(S3Exception.class);
        when(notFound.statusCode()).thenReturn(404);

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(notFound);

        initializer.init();

        verify(s3Client, times(1)).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, times(1)).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("logs warning on non-404 S3Exception without creating bucket")
    void non404S3Exception() {
        final S3Exception forbidden = mock(S3Exception.class);
        when(forbidden.statusCode()).thenReturn(403);

        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(forbidden);

        initializer.init();

        verify(s3Client, times(1)).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    @DisplayName("logs warning on SdkClientException (connectivity issue) without creating bucket")
    void sdkClientException() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(SdkClientException.create("Connection refused"));

        initializer.init();

        verify(s3Client, times(1)).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }
}
