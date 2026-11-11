package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.OcrProperties;
import at.capadoc.ocrworker.exception.OcrPermanentException;
import at.capadoc.ocrworker.exception.OcrTransientException;
import at.capadoc.ocrworker.exception.StorageException;
import at.capadoc.ocrworker.exception.StorageObjectNotFoundException;
import at.capadoc.ocrworker.model.IndexingMessage;
import at.capadoc.ocrworker.model.OcrMessage;
import at.capadoc.ocrworker.model.ProcessingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class OcrMessageListener {

    private final StorageService storageService;
    private final OcrService ocrService;
    private final IndexingMessagePublisher indexingMessagePublisher;
    private final DocumentStatusUpdater documentStatusUpdater;
    private final OcrProperties ocrProperties;

    @Autowired
    public OcrMessageListener(
            StorageService storageService,
            OcrService ocrService,
            IndexingMessagePublisher indexingMessagePublisher,
            DocumentStatusUpdater documentStatusUpdater,
            OcrProperties ocrProperties) {
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.indexingMessagePublisher = indexingMessagePublisher;
        this.documentStatusUpdater = documentStatusUpdater;
        this.ocrProperties = ocrProperties;
    }

    public OcrMessageListener(
            StorageService storageService,
            OcrService ocrService,
            IndexingMessagePublisher indexingMessagePublisher,
            DocumentStatusUpdater documentStatusUpdater) {
        this(storageService, ocrService, indexingMessagePublisher, documentStatusUpdater, new OcrProperties());
    }

    public OcrMessageListener() {
        this(null, null, null, null, new OcrProperties());
    }

    @RabbitListener(queues = "${capadoc.rabbitmq.queue}")
    public void handleOcrMessage(OcrMessage message) {
        if (message == null || message.getDocumentId() == null) {
            log.warn("Received invalid OCR message without documentId; ignoring.");
            return;
        }

        Long documentId = message.getDocumentId();
        String storageKey = (message.getStorageKey() != null && !message.getStorageKey().isBlank())
                ? message.getStorageKey()
                : StorageService.DOCUMENT_KEY_PREFIX + documentId + StorageService.DOCUMENT_KEY_SUFFIX;

        log.info("Received OCR message: documentId={}, storageKey={}, filename={}",
                documentId, storageKey, message.getFilename());

        // Fallback für einfachen Platzhalter-Aufruf ohne verdrahtete Dependencies
        if (storageService == null || ocrService == null) {
            log.info("OCR processing placeholder for documentId={}, filename={}",
                    documentId, message.getFilename());
            return;
        }

        // Idempotenz-Prüfung: Bereits erfolgreich verarbeitete Dokumente nicht erneut verarbeiten
        if (documentStatusUpdater != null) {
            Optional<ProcessingStatus> currentStatus = documentStatusUpdater.getOcrStatus(documentId);
            if (currentStatus.isPresent() && currentStatus.get() == ProcessingStatus.DONE) {
                log.info("Document documentId={} already has ocrStatus=DONE; skipping duplicate OCR message idempotently.",
                        documentId);
                return;
            }
        }

        int maxAttempts = Math.max(1, ocrProperties != null ? ocrProperties.getMaxRetries() : 3);
        long backoffMs = Math.max(0L, ocrProperties != null ? ocrProperties.getRetryBackoffMs() : 500L);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] pdfBytes = storageService.download(storageKey);
                String extractedText = ocrService.extractText(pdfBytes);

                log.info("OCR completed for documentId={} (extracted {} characters)",
                        documentId, extractedText != null ? extractedText.length() : 0);

                if (documentStatusUpdater != null) {
                    documentStatusUpdater.updateOcrStatus(documentId, ProcessingStatus.DONE);
                }

                if (indexingMessagePublisher != null) {
                    IndexingMessage indexingMessage = IndexingMessage.builder()
                            .documentId(documentId)
                            .storageKey(storageKey)
                            .filename(message.getFilename())
                            .extractedText(extractedText != null ? extractedText : "")
                            .processedAt(LocalDateTime.now())
                            .build();
                    indexingMessagePublisher.publishIndexingMessage(indexingMessage);
                }
                return;
            } catch (StorageObjectNotFoundException | OcrPermanentException ex) {
                log.error("Permanent OCR failure for documentId={}, storageKey={} (no retry): {}",
                        documentId, storageKey, ex.getMessage());
                markFailed(documentId);
                return;
            } catch (OcrTransientException | StorageException ex) {
                log.warn("Transient OCR error on attempt {}/{} for documentId={}, storageKey={}: {}",
                        attempt, maxAttempts, documentId, storageKey, ex.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Exhausted all {} retry attempts for documentId={}; marking FAILED and sending to DLQ.",
                            maxAttempts, documentId, ex);
                    markFailed(documentId);
                    if (indexingMessagePublisher != null) {
                        indexingMessagePublisher.publishToDeadLetterQueue(message);
                    }
                    return;
                }
                sleepBackoff(backoffMs * (1L << (attempt - 1)), documentId);
            } catch (Exception ex) {
                log.error("Unexpected error while processing OCR for documentId={}, storageKey={}",
                        documentId, storageKey, ex);
                markFailed(documentId);
                return;
            }
        }
    }

    private void markFailed(Long documentId) {
        if (documentStatusUpdater != null) {
            documentStatusUpdater.updateOcrStatus(documentId, ProcessingStatus.FAILED);
        }
    }

    private void sleepBackoff(long delayMs, Long documentId) {
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new OcrTransientException("Interrupted during retry backoff for documentId=" + documentId, ie);
        }
    }
}
