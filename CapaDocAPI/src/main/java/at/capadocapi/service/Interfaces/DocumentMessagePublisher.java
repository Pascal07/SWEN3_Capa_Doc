package at.capadocapi.service.Interfaces;

import at.capadocapi.model.dto.OcrMessage;

public interface DocumentMessagePublisher {
    void publishOcrMessage(OcrMessage message);

    default void publishDeleteIndexMessage(Long documentId) {
        // default no-op for backward compatibility
    }
}