package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.config.OcrProperties;
import at.capadoc.ocrworker.exception.OcrPermanentException;
import at.capadoc.ocrworker.exception.OcrTransientException;
import at.capadoc.ocrworker.exception.StorageException;
import at.capadoc.ocrworker.exception.StorageObjectNotFoundException;
import at.capadoc.ocrworker.model.IndexingMessage;
import at.capadoc.ocrworker.model.OcrMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class OcrMessageListener {

    private final StorageService storageService;
    private final OcrService ocrService;
    private final IndexingMessagePublisher indexingMessagePublisher;
    private final StatusPublisher statusPublisher;
    private final OcrProperties ocrProperties;

    @Autowired
    public OcrMessageListener(
            StorageService storageService,
            OcrService ocrService,
            IndexingMessagePublisher indexingMessagePublisher,
            StatusPublisher statusPublisher,
            OcrProperties ocrProperties) {
        this.storageService = storageService;
        this.ocrService = ocrService;
        this.indexingMessagePublisher = indexingMessagePublisher;
        this.statusPublisher = statusPublisher;
        this.ocrProperties = ocrProperties;
    }

    public OcrMessageListener(
            StorageService storageService,
            OcrService ocrService,
            IndexingMessagePublisher indexingMessagePublisher,
            StatusPublisher statusPublisher) {
        this(storageService, ocrService, indexingMessagePublisher, statusPublisher, new OcrProperties());
    }

    public OcrMessageListener() {
        this(null, null, null, null, new OcrProperties());
    }

    @RabbitListener(
            queues = "${capadoc.rabbitmq.queue}",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
    )
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

        if (statusPublisher != null) {
            statusPublisher.publishInProgress(documentId);
        }

        int maxAttempts = Math.max(1, ocrProperties != null ? ocrProperties.getMaxRetries() : 3);
        long backoffMs = Math.max(0L, ocrProperties != null ? ocrProperties.getRetryBackoffMs() : 500L);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                byte[] pdfBytes = storageService.download(storageKey);
                String extractedText = ocrService.extractText(pdfBytes);

                log.info("OCR completed for documentId={} (extracted {} characters)",
                        documentId, extractedText != null ? extractedText.length() : 0);

                // Reihenfolge im Erfolgsfall: Erst Folgenachricht an Indexing-Queue senden, dann DONE melden
                if (indexingMessagePublisher != null) {
                    IndexingMessage indexingMessage = IndexingMessage.builder()
                            .documentId(documentId)
                            .storageKey(storageKey)
                            .filename(message.getFilename())
                            .extractedText(extractedText != null ? extractedText : "")
                            .uploadedAt(message.getUploadedAt())
                            .processedAt(LocalDateTime.now())
                            .build();
                    indexingMessagePublisher.publishIndexingMessage(indexingMessage);
                }

                if (statusPublisher != null) {
                    statusPublisher.publishDone(documentId);
                }
                return;
            } catch (StorageObjectNotFoundException | OcrPermanentException ex) {
                log.error("Permanent OCR failure for documentId={}, storageKey={} (no retry): {}",
                        documentId, storageKey, ex.getMessage());
                markFailed(documentId, ex.getMessage());
                return;
            } catch (OcrTransientException | StorageException ex) {
                log.warn("Transient OCR error on attempt {}/{} for documentId={}, storageKey={}: {}",
                        attempt, maxAttempts, documentId, storageKey, ex.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Exhausted all {} retry attempts for documentId={}; marking FAILED and sending to DLQ.",
                            maxAttempts, documentId, ex);
                    markFailed(documentId, ex.getMessage());
                    if (indexingMessagePublisher != null) {
                        indexingMessagePublisher.publishToDeadLetterQueue(message);
                    }
                    return;
                }
                sleepBackoff(backoffMs * (1L << (attempt - 1)), documentId);
            } catch (Exception ex) {
                log.error("Unexpected error while processing OCR for documentId={}, storageKey={}",
                        documentId, storageKey, ex);
                markFailed(documentId, ex.getMessage());
                return;
            }
        }
    }

    private void markFailed(Long documentId, String errorMessage) {
        if (statusPublisher != null) {
            String safeMessage = (errorMessage != null && !errorMessage.isBlank())
                    ? errorMessage
                    : "OCR processing failed";
            statusPublisher.publishFailed(documentId, safeMessage);
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
