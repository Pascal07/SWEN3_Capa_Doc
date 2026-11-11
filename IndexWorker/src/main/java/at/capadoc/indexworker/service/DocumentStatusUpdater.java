package at.capadoc.indexworker.service;

import at.capadoc.indexworker.model.ProcessingStatus;

import java.time.LocalDateTime;
import java.util.Optional;

public interface DocumentStatusUpdater {

    Optional<ProcessingStatus> getIndexStatus(Long documentId);

    void updateIndexStatus(Long documentId, ProcessingStatus status);

    Optional<LocalDateTime> getUploadedAt(Long documentId);
}
