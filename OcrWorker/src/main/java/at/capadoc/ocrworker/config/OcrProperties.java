package at.capadoc.ocrworker.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    private String languages = "deu+eng";
    private int dpi = 300;
    private Duration timeoutPerPage = Duration.ofSeconds(30);
    private String tesseractCommand = "tesseract";
    private String ghostscriptCommand = "gs";
    private String tempDir = "";
    private int maxPages = 100;
    private long maxPdfSizeBytes = 20L * 1024L * 1024L;
    private int maxRetries = 3;
    private long retryBackoffMs = 500L;
}
