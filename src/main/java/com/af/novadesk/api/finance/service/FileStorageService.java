package com.af.novadesk.api.common.service;

import com.af.novadesk.api.config.MinioProperties;
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
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstraction over S3-compatible object storage (MinIO / AWS S3).
 * <p>
 * Provides simple file operations: upload, download, delete, and list.
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final S3Client    s3Client;
    private final S3Presigner s3Presigner;
    private final String      bucketName;

    public FileStorageService(final S3Client s3Client,
                              final S3Presigner s3Presigner,
                              final MinioProperties minioProperties) {
        this.s3Client    = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName  = minioProperties.bucket();
    }

    private static void validateKey(final String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Object key must not be null or blank");
        }
        if (key.contains("..")) {
            throw new IllegalArgumentException("Object key must not contain '..' (path traversal detected)");
        }
        if (key.startsWith("/")) {
            throw new IllegalArgumentException("Object key must not start with '/'");
        }
    }

    /**
     * Uploads data to the configured bucket, streaming directly from an {@link InputStream}.
     * <p>
     * This is the preferred method for large payloads — the content is streamed to S3
     * without buffering the entire file in the JVM heap, avoiding {@link OutOfMemoryError}
     * under concurrent or large-file workloads.
     *
     * @param key           object key (path within the bucket)
     * @param data          stream providing the file content
     * @param contentLength exact number of bytes in the stream (must be known beforehand)
     * @param contentType   MIME type (e.g. {@code application/pdf}, {@code image/png})
     * @return the {@link PutObjectResponse} from S3
     */
    public PutObjectResponse upload(final String key, final InputStream data, final long contentLength, final String contentType) {
        validateKey(key);
        log.debug("Uploading object '{}' to bucket '{}' ({} bytes)", key, bucketName, contentLength);
        return s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build(),
            RequestBody.fromInputStream(data, contentLength)
        );
    }

    /**
     * Uploads a byte array to the configured bucket.
     * <p>
     * Prefer {@link #upload(String, InputStream, long, String)} for large files to
     * avoid loading the entire payload into heap memory.
     *
     * @param key         object key (path within the bucket)
     * @param data        file content as bytes
     * @param contentType MIME type (e.g. {@code application/pdf}, {@code image/png})
     * @return the {@link PutObjectResponse} from S3
     */
    public PutObjectResponse upload(final String key, final byte[] data, final String contentType) {
        log.debug("Uploading object '{}' to bucket '{}' ({} bytes)", key, bucketName, data.length);
        return upload(key, new java.io.ByteArrayInputStream(data), data.length, contentType);
    }

    /**
     * Downloads an object's content as an {@link InputStream}.
     * <p>
     * <strong>The caller is responsible for closing the returned stream.</strong>
     * The underlying {@code ResponseInputStream} holds an open HTTP connection to the
     * S3-compatible store; failure to close it will leak the connection until it is
     * eventually timed out by the server, potentially exhausting the connection pool
     * under load.
     * </p>
     * <p>Prefer {@link #download(String, OutputStream)} when you want to write
     * directly to a local file or another sink — it manages the stream lifecycle
     * automatically and avoids the leak risk entirely.</p>
     *
     * @param key object key (path within the bucket)
     * @return the object content as an input stream (must be closed by the caller)
     */
    public InputStream download(final String key) {
        validateKey(key);
        log.debug("Downloading object '{}' from bucket '{}'", key, bucketName);
        return s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        );
    }

    /**
     * Downloads an object's content and writes it to the given {@link OutputStream}.
     * <p>
     * The S3 response stream is closed automatically after the write completes,
     * eliminating the connection-leak risk inherent in the raw
     * {@link #download(String)} variant.
     * </p>
     *
     * @param key  object key (path within the bucket)
     * @param sink the output stream to write the object content into
     */
    public void download(final String key, final OutputStream sink) {
        log.debug("Downloading object '{}' from bucket '{}' to output stream", key, bucketName);
        try (final InputStream in = download(key)) {
            in.transferTo(sink);
        } catch (final java.io.IOException e) {
            throw new RuntimeException("Failed to download object '" + key + "' from bucket '" + bucketName + "'", e);
        }
    }

    /**
     * Generates a short-lived pre-signed URL that allows the holder to download
     * the object at {@code key} without any additional authentication.
     *
     * <p>The URL is valid for the duration specified by {@code expiry}. Callers
     * should never cache or store these URLs — generate a fresh one per request.</p>
     *
     * <p><strong>Never expose the raw {@code storageKey} to clients.</strong>
     * Always use this method to issue a time-limited URL instead.</p>
     *
     * @param key    object key (path within the bucket)
     * @param expiry how long the URL should remain valid (e.g. {@code Duration.ofMinutes(15)})
     * @return the pre-signed URL as a string
     */
    public String generatePresignedUrl(final String key, final Duration expiry) {
        validateKey(key);
        log.debug("Generating pre-signed URL for object '{}' (expires in {})", key, expiry);

        final PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build())
                .build()
        );

        return presigned.url().toString();
    }

    /**
     * Deletes an object from the configured bucket.
     *
     * @param key object key (path within the bucket)
     * @return the {@link DeleteObjectResponse} from S3
     */
    public DeleteObjectResponse delete(final String key) {
        validateKey(key);
        log.debug("Deleting object '{}' from bucket '{}'", key, bucketName);
        return s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        );
    }

    /**
     * Lists objects under a given prefix (single page).
     * <p>
     * <strong>Note:</strong> S3 returns at most 1,000 objects per page. If more objects
     * match the prefix, {@link ListObjectsV2Response#isTruncated()} will be {@code true}
     * and the remaining results are not included in this response.
     * </p>
     * <p>Prefer {@link #listAll(String)} for use cases that need the complete set of
     * matching objects — it paginates automatically.</p>
     *
     * @param prefix object key prefix to filter by
     * @return the {@link ListObjectsV2Response} from S3 (capped at 1,000 objects)
     */
    public ListObjectsV2Response list(final String prefix) {
        validateKey(prefix);
        log.debug("Listing objects with prefix '{}' from bucket '{}'", prefix, bucketName);
        return s3Client.listObjectsV2(
            ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build()
        );
    }

    /**
     * Lists all objects under a given prefix, automatically following paginated
     * responses until the full result set has been collected.
     * <p>
     * Internally this fetches successive pages using the continuation token
     * supplied by S3, so the returned list contains <em>all</em> matching objects
     * regardless of the 1,000-object-per-page limit.
     * </p>
     * <p>
     * For buckets containing a very large number of objects under this prefix
     * (millions or more), callers should consider using the single-page
     * {@link #list(String)} variant and driving pagination themselves to avoid
     * unbounded memory consumption.
     * </p>
     *
     * @param prefix object key prefix to filter by
     * @return a complete list of all matching objects across all pages
     */
    public List<S3Object> listAll(final String prefix) {
        validateKey(prefix);
        log.debug("Listing all objects with prefix '{}' from bucket '{}'", prefix, bucketName);
        final List<S3Object> all = new ArrayList<>();
        String token = null;
        do {
            final ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .continuationToken(token)
                    .build()
            );
            all.addAll(response.contents());
            token = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (token != null);
        log.debug("Retrieved {} total objects with prefix '{}'", all.size(), prefix);
        return all;
    }
}
