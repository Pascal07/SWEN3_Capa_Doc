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
public class OcrMessage {
    private Long documentId;
    private String storageKey;
    private String filename;
    private String contentType;
    private LocalDateTime uploadedAt;

    public OcrMessage(Long documentId, String filename, String contentType, LocalDateTime uploadedAt) {
        this.documentId = documentId;
        this.filename = filename;
        this.contentType = contentType;
        this.uploadedAt = uploadedAt;
    }

    public OcrMessage(Long documentId, String storageKey) {
        this.documentId = documentId;
        this.storageKey = storageKey;
    }
}
