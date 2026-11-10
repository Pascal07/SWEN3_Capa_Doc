package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.ProcessingStatus;

import java.util.Optional;

public interface DocumentStatusUpdater {

    Optional<ProcessingStatus> getOcrStatus(Long documentId);

    void updateOcrStatus(Long documentId, ProcessingStatus status);
}
