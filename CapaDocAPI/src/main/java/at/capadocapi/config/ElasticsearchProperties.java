package at.capadocapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    private String url = "http://localhost:9200";
    private String indexName = "documents";
    private String username = "";
    private String password = "";
    private boolean autoCreateIndex = true;
    private int maxRetries = 10;
    private long retryDelayMs = 2000L;
}
