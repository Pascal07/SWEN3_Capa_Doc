package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.DocumentStatusEvent;
import at.capadoc.ocrworker.model.ProcessingStage;
import at.capadoc.ocrworker.model.ProcessingStatus;

import java.time.LocalDateTime;

public interface StatusPublisher {

    void publishStatus(DocumentStatusEvent event);

    default void publishInProgress(Long documentId) {
        publishStatus(DocumentStatusEvent.builder()
                .documentId(documentId)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.IN_PROGRESS)
                .timestamp(LocalDateTime.now())
                .build());
    }

    default void publishDone(Long documentId) {
        publishStatus(DocumentStatusEvent.builder()
                .documentId(documentId)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.DONE)
                .timestamp(LocalDateTime.now())
                .build());
    }

    default void publishFailed(Long documentId, String errorMessage) {
        publishStatus(DocumentStatusEvent.builder()
                .documentId(documentId)
                .stage(ProcessingStage.OCR)
                .status(ProcessingStatus.FAILED)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
