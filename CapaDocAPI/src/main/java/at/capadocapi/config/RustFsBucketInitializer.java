package at.capadocapi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "storage.rustfs.auto-create-bucket", havingValue = "true", matchIfMissing = true)
public class RustFsBucketInitializer implements ApplicationRunner {

    private final S3Client s3Client;
    private final RustFsProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        ensureBucketExists();
    }

    public void ensureBucketExists() {
        String bucket = properties.getBucket();
        int maxRetries = Math.max(1, properties.getMaxRetries());
        long delayMs = Math.max(0L, properties.getRetryDelayMs());

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                if (bucketExists(bucket)) {
                    log.info("RustFS bucket '{}' already exists.", bucket);
                    return;
                }
                createBucketIdempotent(bucket);
                log.info("RustFS bucket '{}' created successfully.", bucket);
                return;
            } catch (SdkException ex) {
                log.warn("Attempt {}/{} to initialize RustFS bucket '{}' failed: {}",
                        attempt, maxRetries, bucket, ex.getMessage());
                if (attempt == maxRetries) {
                    throw new IllegalStateException(
                            "Could not initialize RustFS bucket '" + bucket + "' after " + maxRetries + " attempts", ex);
                }
                sleep(delayMs);
            }
        }
    }

    private boolean bucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private void createBucketIdempotent(String bucket) {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyExistsException | BucketAlreadyOwnedByYouException ex) {
            log.info("RustFS bucket '{}' was created concurrently.", bucket);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 409) {
                log.info("RustFS bucket '{}' already exists (HTTP 409).", bucket);
                return;
            }
            throw ex;
        }
    }

    private void sleep(long delayMs) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry RustFS bucket initialization", ie);
        }
    }
}
