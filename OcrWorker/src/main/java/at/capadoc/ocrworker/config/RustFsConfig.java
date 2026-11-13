package at.capadoc.ocrworker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
@EnableConfigurationProperties({OcrProperties.class, RustFsProperties.class})
public class RustFsConfig {

    @Bean
    public S3Client s3Client(RustFsProperties properties) {
        String accessKey = (properties.getAccessKey() == null || properties.getAccessKey().isBlank())
                ? "placeholder-access-key"
                : properties.getAccessKey();
        String secretKey = (properties.getSecretKey() == null || properties.getSecretKey().isBlank())
                ? "placeholder-secret-key"
                : properties.getSecretKey();

        return S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(properties.getRegion()))
                .build();
    }
}
