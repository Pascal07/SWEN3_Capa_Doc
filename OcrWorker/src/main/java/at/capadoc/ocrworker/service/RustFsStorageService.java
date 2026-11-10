package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.RustFsProperties;
import at.capadoc.ocrworker.exception.StorageException;
import at.capadoc.ocrworker.exception.StorageObjectNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
@RequiredArgsConstructor
public class RustFsStorageService implements StorageService {

    private final S3Client s3Client;
    private final RustFsProperties properties;

    @Override
    public void upload(String key, byte[] content, String contentType) {
        validateKey(key);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();
        try {
            s3Client.putObject(request, RequestBody.fromBytes(content));
        } catch (SdkException ex) {
            throw new StorageException("Failed to upload object with key: " + key, ex);
        }
    }

    @Override
    public byte[] download(String key) {
        validateKey(key);
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build();
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(request);
            return response.asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new StorageObjectNotFoundException(key, ex);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new StorageObjectNotFoundException(key, ex);
            }
            throw new StorageException("Failed to download object with key: " + key, ex);
        } catch (SdkException ex) {
            throw new StorageException("Failed to download object with key: " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build();
        try {
            s3Client.deleteObject(request);
        } catch (NoSuchKeyException ex) {
            log.warn("Object '{}' not found in bucket '{}' during delete; ignoring idempotently.",
                    key, properties.getBucket());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return;
            }
            throw new StorageException("Failed to delete object with key: " + key, ex);
        } catch (SdkException ex) {
            throw new StorageException("Failed to delete object with key: " + key, ex);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage object key must not be null or blank");
        }
    }
}
