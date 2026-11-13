package at.capadoc.ocrworker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "storage.rustfs")
public class RustFsProperties {

    private String endpoint = "http://localhost:9000";
    private String accessKey;
    private String secretKey;
    private String bucket = "documents";
    private String region = "us-east-1";
}
