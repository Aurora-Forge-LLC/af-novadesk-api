package com.af.novadesk.api.common.service;

import com.af.novadesk.api.config.MinioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService")
class FileStorageServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "path/to/object.pdf";
    private static final String CONTENT_TYPE = "application/pdf";

    @Mock
    private S3Client s3Client;

    @Mock
    private MinioProperties minioProperties;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        when(minioProperties.bucket()).thenReturn(BUCKET);
        service = new FileStorageService(s3Client, minioProperties);
    }

    // --------------- constructor ---------------

    @Test
    @DisplayName("constructs with bucket name from MinioProperties")
    void constructsWithBucketFromProperties() {
        assertThat(service).isNotNull();
        verify(minioProperties).bucket();
    }

    // --------------- upload (stream) ---------------

    @Nested
    @DisplayName("upload(InputStream)")
    class UploadStream {

        @Captor
        private ArgumentCaptor<PutObjectRequest> requestCaptor;

        @Test
        @DisplayName("delegates to S3Client.putObject with correct bucket, key, contentType and length")
        void delegatesToS3Client() {
            final byte[] content = "hello".getBytes();
            final InputStream data = new ByteArrayInputStream(content);
            final PutObjectResponse expectedResponse = PutObjectResponse.builder().build();

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(expectedResponse);

            final PutObjectResponse response = service.upload(KEY, data, content.length, CONTENT_TYPE);

            assertThat(response).isSameAs(expectedResponse);
            verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

            final PutObjectRequest req = requestCaptor.getValue();
            assertThat(req.bucket()).isEqualTo(BUCKET);
            assertThat(req.key()).isEqualTo(KEY);
            assertThat(req.contentType()).isEqualTo(CONTENT_TYPE);
            assertThat(req.contentLength()).isEqualTo(content.length);
        }

        @Test
        @DisplayName("rejects null key")
        void rejectsNullKey() {
            final InputStream data = new ByteArrayInputStream(new byte[0]);
            assertThatThrownBy(() -> service.upload(null, data, 0, CONTENT_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
        }

        @Test
        @DisplayName("rejects blank key")
        void rejectsBlankKey() {
            final InputStream data = new ByteArrayInputStream(new byte[0]);
            assertThatThrownBy(() -> service.upload("   ", data, 0, CONTENT_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
        }

        @Test
        @DisplayName("rejects key with path traversal")
        void rejectsPathTraversal() {
            final InputStream data = new ByteArrayInputStream(new byte[0]);
            assertThatThrownBy(() -> service.upload("safe/../../etc/passwd", data, 0, CONTENT_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
        }

        @Test
        @DisplayName("rejects key starting with slash")
        void rejectsLeadingSlash() {
            final InputStream data = new ByteArrayInputStream(new byte[0]);
            assertThatThrownBy(() -> service.upload("/etc/passwd", data, 0, CONTENT_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not start with '/'");
        }
    }

    // --------------- upload (byte[]) ---------------

    @Nested
    @DisplayName("upload(byte[])")
    class UploadBytes {

        @Test
        @DisplayName("delegates to stream-based upload")
        void delegatesToStreamUpload() {
            final byte[] content = "data".getBytes();
            final PutObjectResponse expectedResponse = PutObjectResponse.builder().build();

            when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(expectedResponse);

            final PutObjectResponse response = service.upload(KEY, content, CONTENT_TYPE);

            assertThat(response).isSameAs(expectedResponse);
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }
    }

    // --------------- download (stream) ---------------

    @Nested
    @DisplayName("download(String)")
    class DownloadStream {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("returns the response stream from S3Client.getObject")
        void returnsInputStream() {
            final ResponseInputStream<GetObjectResponse> expectedStream =
                new ResponseInputStream<>(
                    GetObjectResponse.builder().build(),
                    new ByteArrayInputStream("content".getBytes()));
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(expectedStream);

            final InputStream result = service.download(KEY);

            assertThat(result).isSameAs(expectedStream);
            verify(s3Client).getObject(any(GetObjectRequest.class));
        }
    }

    // --------------- download (OutputStream) ---------------

    @Nested
    @DisplayName("download(String, OutputStream)")
    class DownloadToSink {

        @Test
        @DisplayName("writes content to the output stream")
        void writesToOutputStream() throws Exception {
            final byte[] content = "file-content".getBytes();
            final ResponseInputStream<GetObjectResponse> stream =
                new ResponseInputStream<>(
                    GetObjectResponse.builder().build(),
                    new ByteArrayInputStream(content));
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(stream);

            final ByteArrayOutputStream sink = new ByteArrayOutputStream();
            service.download(KEY, sink);

            assertThat(sink.toByteArray()).isEqualTo(content);
        }

        @Test
        @DisplayName("wraps IOException in RuntimeException")
        void wrapsIOException() {
            final InputStream brokenStream = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw new IOException("connection lost");
                }
            };
            final ResponseInputStream<GetObjectResponse> failingStream =
                new ResponseInputStream<>(
                    GetObjectResponse.builder().build(),
                    brokenStream);
            when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(failingStream);

            final OutputStream sink = new ByteArrayOutputStream();
            assertThatThrownBy(() -> service.download(KEY, sink))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to download");
        }
    }

    // --------------- delete ---------------

    @Nested
    @DisplayName("delete(String)")
    class Delete {

        @Test
        @DisplayName("delegates to S3Client.deleteObject with correct bucket and key")
        void delegatesToS3Client() {
            final DeleteObjectResponse expectedResponse = DeleteObjectResponse.builder().build();
            when(s3Client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(expectedResponse);

            final DeleteObjectResponse response = service.delete(KEY);

            assertThat(response).isSameAs(expectedResponse);
            verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    // --------------- list ---------------

    @Nested
    @DisplayName("list(String)")
    class ListSinglePage {

        @Test
        @DisplayName("delegates to S3Client.listObjectsV2 with correct bucket and prefix")
        void delegatesToS3Client() {
            final ListObjectsV2Response expectedResponse = ListObjectsV2Response.builder().build();
            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(expectedResponse);

            final ListObjectsV2Response response = service.list("prefix/");

            assertThat(response).isSameAs(expectedResponse);
            verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        }
    }

    // --------------- listAll ---------------

    @Nested
    @DisplayName("listAll(String)")
    class ListAll {

        @Test
        @DisplayName("returns all objects across multiple pages")
        void paginatesThroughAllPages() {
            final S3Object obj1 = S3Object.builder().key("a.txt").build();
            final S3Object obj2 = S3Object.builder().key("b.txt").build();
            final S3Object obj3 = S3Object.builder().key("c.txt").build();

            final ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .contents(obj1, obj2)
                .isTruncated(true)
                .nextContinuationToken("token-2")
                .build();
            final ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .contents(obj3)
                .isTruncated(false)
                .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page1, page2);

            final List<S3Object> result = service.listAll("prefix/");

            assertThat(result).containsExactly(obj1, obj2, obj3);
            verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        }

        @Test
        @DisplayName("handles single page with no truncation")
        void singlePage() {
            final S3Object obj = S3Object.builder().key("a.txt").build();
            final ListObjectsV2Response page = ListObjectsV2Response.builder()
                .contents(obj)
                .isTruncated(false)
                .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page);

            final List<S3Object> result = service.listAll("prefix/");

            assertThat(result).containsExactly(obj);
            verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        }
    }
}
