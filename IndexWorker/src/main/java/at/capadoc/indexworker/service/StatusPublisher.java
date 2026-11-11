package at.capadoc.indexworker.service;

import at.capadoc.indexworker.model.DocumentStatusEvent;
import at.capadoc.indexworker.model.ProcessingStage;
import at.capadoc.indexworker.model.ProcessingStatus;

import java.time.LocalDateTime;

public interface StatusPublisher {

    void publishStatus(DocumentStatusEvent event);

    default void publishInProgress(Long documentId) {
        publishStatus(DocumentStatusEvent.builder()
                .documentId(documentId)
                .stage(ProcessingStage.INDEXING)
                .status(ProcessingStatus.IN_PROGRESS)
                .timestamp(LocalDateTime.now())
                .build());
    }

    default void publishDone(Long documentId) {
        publishStatus(DocumentStatusEvent.builder()
                .documentId(documentId)
                .stage(ProcessingStage.INDEXING)
                .status(ProcessingStatus.DONE)
                .timestamp(LocalDateTime.now())
                .build());
    }

    default void publishFailed(Long documentId, String errorMessage) {
        publishStatus(DocumentStatusEvent.builder()
                .documentId(documentId)
                .stage(ProcessingStage.INDEXING)
                .status(ProcessingStatus.FAILED)
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build());
    }
}
