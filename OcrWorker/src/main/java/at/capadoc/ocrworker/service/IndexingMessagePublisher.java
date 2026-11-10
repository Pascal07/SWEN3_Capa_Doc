package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.IndexingMessage;
import at.capadoc.ocrworker.model.OcrMessage;

public interface IndexingMessagePublisher {

    void publishIndexingMessage(IndexingMessage message);

    default void publishToDeadLetterQueue(OcrMessage failedMessage) {
        // Optional hook for forwarding exhausted transient failures to DLQ
    }
}
