package at.capadocapi.service;

import at.capadocapi.model.DocumentEntity;
import at.capadocapi.model.ProcessingStage;
import at.capadocapi.model.ProcessingStatus;
import at.capadocapi.model.dto.DocumentStatusEvent;
import at.capadocapi.repository.DocumentRepository;
import at.capadocapi.service.Interfaces.DocumentStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStatusServiceImpl implements DocumentStatusService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public void updateDocumentStatus(DocumentStatusEvent event) {
        validateEvent(event);

        Long documentId = event.getDocumentId();
        ProcessingStage stage = event.getStage();
        ProcessingStatus newStatus = event.getStatus();

        Optional<DocumentEntity> documentOpt = documentRepository.findById(documentId);
        if (documentOpt.isEmpty()) {
            log.warn("Received DocumentStatusEvent for non-existent or deleted documentId={}, stage={}, status={}; acknowledging without update.",
                    documentId, stage, newStatus);
            return;
        }

        DocumentEntity document = documentOpt.get();
        ProcessingStatus currentStatus = (stage == ProcessingStage.OCR)
                ? document.getOcrStatus()
                : document.getIndexStatus();

        // Rückschrittsicherheit & Idempotenz: Bereits abgeschlossene Stages (DONE) niemals zurücksetzen
        if (currentStatus == ProcessingStatus.DONE) {
            if (newStatus == ProcessingStatus.DONE) {
                log.info("Ignoring duplicate DONE status event for documentId={}, stage={}", documentId, stage);
            } else {
                log.warn("Ignoring out-of-order status transition from DONE to {} for documentId={}, stage={}",
                        newStatus, documentId, stage);
            }
            return;
        }

        String sanitizedError = sanitizeErrorMessage(event.getErrorMessage());

        // Idempotenz bei identischem Status-Event
        if (currentStatus == newStatus) {
            if (newStatus != ProcessingStatus.FAILED || Objects.equals(document.getErrorMessage(), sanitizedError)) {
                log.info("Ignoring duplicate status event for documentId={}, stage={}, status={}",
                        documentId, stage, newStatus);
                return;
            }
        }

        if (stage == ProcessingStage.OCR) {
            document.setOcrStatus(newStatus);
        } else if (stage == ProcessingStage.INDEXING) {
            document.setIndexStatus(newStatus);
        }

        if (newStatus == ProcessingStatus.FAILED) {
            document.setErrorMessage(sanitizedError != null ? sanitizedError : (stage + " processing failed"));
        } else if (newStatus == ProcessingStatus.DONE
                && document.getOcrStatus() != ProcessingStatus.FAILED
                && document.getIndexStatus() != ProcessingStatus.FAILED) {
            document.setErrorMessage(null);
        }

        documentRepository.save(document);
        log.info("Updated {} status to {} for documentId={}", stage, newStatus, documentId);
    }

    private void validateEvent(DocumentStatusEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("DocumentStatusEvent must not be null");
        }
        if (event.getDocumentId() == null || event.getDocumentId() <= 0) {
            throw new IllegalArgumentException("DocumentStatusEvent.documentId must be a positive number");
        }
        if (event.getStage() == null) {
            throw new IllegalArgumentException("DocumentStatusEvent.stage must not be null");
        }
        if (event.getStatus() == null) {
            throw new IllegalArgumentException("DocumentStatusEvent.status must not be null");
        }
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        String trimmed = errorMessage.trim();
        return trimmed.length() > MAX_ERROR_MESSAGE_LENGTH
                ? trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : trimmed;
    }
}
