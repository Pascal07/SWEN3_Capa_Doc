package at.capadoc.indexworker.model;

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

    public static final String ACTION_INDEX = "INDEX";
    public static final String ACTION_DELETE = "DELETE";

    private Long documentId;
    private String storageKey;
    private String filename;
    private String extractedText;
    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;
    private String action;

    public IndexingMessage(Long documentId, String filename, String extractedText, LocalDateTime uploadedAt) {
        this.documentId = documentId;
        this.filename = filename;
        this.extractedText = extractedText;
        this.uploadedAt = uploadedAt;
        this.action = ACTION_INDEX;
    }

    public static IndexingMessage deleteMessage(Long documentId) {
        return IndexingMessage.builder()
                .documentId(documentId)
                .action(ACTION_DELETE)
                .build();
    }

    public boolean isDeleteAction() {
        return ACTION_DELETE.equalsIgnoreCase(action);
    }
}
