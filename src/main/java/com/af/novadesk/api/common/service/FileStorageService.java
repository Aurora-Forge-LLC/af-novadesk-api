package com.af.novadesk.api.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.InputStream;

/**
 * Abstraction over S3-compatible object storage (MinIO / AWS S3).
 * <p>
 * Provides simple file operations: upload, download, delete, and list.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final S3Client s3Client;
    private final String bucketName;

    public FileStorageService(final S3Client s3Client, final String minioBucketName) {
        this.s3Client = s3Client;
        this.bucketName = minioBucketName;
    }

    /**
     * Uploads a byte array to the configured bucket.
     *
     * @param key         object key (path within the bucket)
     * @param data        file content as bytes
     * @param contentType MIME type (e.g. {@code application/pdf}, {@code image/png})
     * @return the {@link PutObjectResponse} from S3
     */
    public PutObjectResponse upload(final String key, final byte[] data, final String contentType) {
        log.debug("Uploading object '{}' to bucket '{}'", key, bucketName);
        return s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(data)
        );
    }

    /**
     * Downloads an object's content as an {@link InputStream}.
     *
     * @param key object key (path within the bucket)
     * @return the object content as an input stream
     */
    public InputStream download(final String key) {
        log.debug("Downloading object '{}' from bucket '{}'", key, bucketName);
        return s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        );
    }

    /**
     * Deletes an object from the configured bucket.
     *
     * @param key object key (path within the bucket)
     * @return the {@link DeleteObjectResponse} from S3
     */
    public DeleteObjectResponse delete(final String key) {
        log.debug("Deleting object '{}' from bucket '{}'", key, bucketName);
        return s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        );
    }

    /**
     * Lists objects under a given prefix.
     *
     * @param prefix object key prefix to filter by
     * @return the {@link ListObjectsV2Response} from S3
     */
    public ListObjectsV2Response list(final String prefix) {
        log.debug("Listing objects with prefix '{}' from bucket '{}'", prefix, bucketName);
        return s3Client.listObjectsV2(
            ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build()
        );
    }
}
