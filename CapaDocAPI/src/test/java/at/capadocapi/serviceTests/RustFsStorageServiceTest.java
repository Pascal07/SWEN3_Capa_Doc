package at.capadocapi.serviceTests;

import at.capadocapi.config.RustFsBucketInitializer;
import at.capadocapi.config.RustFsProperties;
import at.capadocapi.exception.StorageException;
import at.capadocapi.exception.StorageObjectNotFoundException;
import at.capadocapi.service.RustFsStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RustFsStorageServiceTest {

    private S3Client s3Client;
    private RustFsProperties properties;
    private RustFsStorageService storageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        properties = new RustFsProperties();
        properties.setBucket("documents");
        properties.setEndpoint("http://localhost:9000");
        properties.setAccessKey("test-access");
        properties.setSecretKey("test-secret");
        properties.setRegion("us-east-1");
        properties.setMaxRetries(3);
        properties.setRetryDelayMs(1L);

        storageService = new RustFsStorageService(s3Client, properties);
    }

    @Test
    void upload_validContent_putsObjectToConfiguredBucket() {
        byte[] pdfBytes = "%PDF-1.7 sample content".getBytes(StandardCharsets.UTF_8);

        storageService.upload("documents/42.pdf", pdfBytes, "application/pdf");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo("documents");
        assertThat(request.key()).isEqualTo("documents/42.pdf");
        assertThat(request.contentType()).isEqualTo("application/pdf");
        assertThat(request.contentLength()).isEqualTo((long) pdfBytes.length);
    }

    @Test
    void upload_whenS3Fails_throwsStorageException() {
        byte[] pdfBytes = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("Connection refused"));

        assertThatThrownBy(() -> storageService.upload("documents/42.pdf", pdfBytes, "application/pdf"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("documents/42.pdf");
    }

    @Test
    void upload_whenKeyBlank_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> storageService.upload("  ", new byte[]{1, 2}, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void download_existingObject_returnsBytes() {
        byte[] expectedBytes = "%PDF-1.7 content".getBytes(StandardCharsets.UTF_8);
        ResponseBytes<GetObjectResponse> responseBytes = ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), expectedBytes);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(responseBytes);

        byte[] result = storageService.download("documents/7.pdf");

        assertThat(result).isEqualTo(expectedBytes);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("documents");
        assertThat(captor.getValue().key()).isEqualTo("documents/7.pdf");
    }

    @Test
    void download_whenNoSuchKey_throwsStorageObjectNotFoundException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("No such key").build());

        assertThatThrownBy(() -> storageService.download("documents/99.pdf"))
                .isInstanceOf(StorageObjectNotFoundException.class)
                .hasMessageContaining("documents/99.pdf");
    }

    @Test
    void download_whenS3Returns404_throwsStorageObjectNotFoundException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThatThrownBy(() -> storageService.download("documents/99.pdf"))
                .isInstanceOf(StorageObjectNotFoundException.class)
                .hasMessageContaining("documents/99.pdf");
    }

    @Test
    void download_whenS3Returns500_throwsStorageException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        assertThatThrownBy(() -> storageService.download("documents/99.pdf"))
                .isInstanceOf(StorageException.class)
                .isNotInstanceOf(StorageObjectNotFoundException.class);
    }

    @Test
    void delete_existingObject_callsDeleteObject() {
        storageService.delete("documents/5.pdf");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("documents");
        assertThat(captor.getValue().key()).isEqualTo("documents/5.pdf");
    }

    @Test
    void delete_whenNoSuchKey_isIdempotentAndDoesNotThrow() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("No such key").build());

        assertThatCode(() -> storageService.delete("documents/5.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void delete_whenS3Returns404_isIdempotentAndDoesNotThrow() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertThatCode(() -> storageService.delete("documents/5.pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void delete_whenS3Returns500_throwsStorageException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        assertThatThrownBy(() -> storageService.delete("documents/5.pdf"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void bucketInitializer_whenBucketDoesNotExist_createsBucket() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("No such bucket").build());

        RustFsBucketInitializer initializer = new RustFsBucketInitializer(s3Client, properties);
        initializer.ensureBucketExists();

        ArgumentCaptor<CreateBucketRequest> captor = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("documents");
    }

    @Test
    void bucketInitializer_whenFirstAttemptFails_retriesAndSucceeds() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(SdkClientException.create("RustFS starting up"))
                .thenThrow(NoSuchBucketException.builder().message("No such bucket").build());

        RustFsBucketInitializer initializer = new RustFsBucketInitializer(s3Client, properties);
        initializer.ensureBucketExists();

        verify(s3Client, times(2)).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, times(1)).createBucket(any(CreateBucketRequest.class));
    }
}
