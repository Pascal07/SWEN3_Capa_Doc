package at.capadocapi.service;

import at.capadocapi.config.RustFsProperties;
import at.capadocapi.exception.StorageException;
import at.capadocapi.exception.StorageObjectNotFoundException;
import at.capadocapi.service.Interfaces.StorageService;
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
        if (content == null) {
            throw new IllegalArgumentException("Content must not be null");
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(content));
            log.info("Uploaded object '{}' ({} bytes) to bucket '{}'",
                    key, content.length, properties.getBucket());
        } catch (SdkException ex) {
            log.error("Failed to upload object '{}' to bucket '{}'", key, properties.getBucket(), ex);
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
            log.warn("Object '{}' not found in bucket '{}'", key, properties.getBucket());
            throw new StorageObjectNotFoundException(key, ex);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                log.warn("Object '{}' not found (404) in bucket '{}'", key, properties.getBucket());
                throw new StorageObjectNotFoundException(key, ex);
            }
            log.error("S3 error while downloading object '{}' from bucket '{}'", key, properties.getBucket(), ex);
            throw new StorageException("Failed to download object with key: " + key, ex);
        } catch (SdkException ex) {
            log.error("Failed to download object '{}' from bucket '{}'", key, properties.getBucket(), ex);
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
            log.info("Deleted object '{}' from bucket '{}'", key, properties.getBucket());
        } catch (NoSuchKeyException ex) {
            log.warn("Object '{}' not found in bucket '{}' during delete; ignoring idempotently.",
                    key, properties.getBucket());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                log.warn("Object '{}' not found (404) in bucket '{}' during delete; ignoring idempotently.",
                        key, properties.getBucket());
                return;
            }
            log.error("S3 error while deleting object '{}' from bucket '{}'", key, properties.getBucket(), ex);
            throw new StorageException("Failed to delete object with key: " + key, ex);
        } catch (SdkException ex) {
            log.error("Failed to delete object '{}' from bucket '{}'", key, properties.getBucket(), ex);
            throw new StorageException("Failed to delete object with key: " + key, ex);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Storage object key must not be null or blank");
        }
    }
}
