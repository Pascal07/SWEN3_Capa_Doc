package at.capadoc.ocrworker.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexingMessage {
    private Long documentId;
    private String storageKey;
    private String filename;
    private String extractedText;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;

    public IndexingMessage(
            Long documentId,
            String storageKey,
            String filename,
            String extractedText,
            LocalDateTime processedAt) {
        this(documentId, storageKey, filename, extractedText, null, processedAt);
    }
}
