package at.capadoc.indexworker.service;

import at.capadoc.indexworker.config.ElasticsearchProperties;
import at.capadoc.indexworker.exception.IndexingPermanentException;
import at.capadoc.indexworker.exception.IndexingTransientException;
import at.capadoc.indexworker.model.IndexingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class IndexingMessageListener {

    private final DocumentIndexService documentIndexService;
    private final StatusPublisher statusPublisher;
    private final IndexingDeadLetterPublisher deadLetterPublisher;
    private final ElasticsearchProperties properties;

    @Autowired
    public IndexingMessageListener(
            DocumentIndexService documentIndexService,
            StatusPublisher statusPublisher,
            IndexingDeadLetterPublisher deadLetterPublisher,
            ElasticsearchProperties properties) {
        this.documentIndexService = documentIndexService;
        this.statusPublisher = statusPublisher;
        this.deadLetterPublisher = deadLetterPublisher;
        this.properties = properties;
    }

    public IndexingMessageListener(
            DocumentIndexService documentIndexService,
            StatusPublisher statusPublisher,
            IndexingDeadLetterPublisher deadLetterPublisher) {
        this(documentIndexService, statusPublisher, deadLetterPublisher, new ElasticsearchProperties());
    }

    @RabbitListener(
            queues = "${capadoc.rabbitmq.indexing.queue:indexing.queue}",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
    )
    public void handleIndexingMessage(IndexingMessage message) {
        if (message == null || message.getDocumentId() == null) {
            log.error("Permanent indexing error: received invalid IndexingMessage without documentId; ignoring without retry.");
            return;
        }

        Long documentId = message.getDocumentId();

        if (message.isDeleteAction()) {
            handleDelete(message);
            return;
        }

        if (message.getFilename() == null || message.getFilename().isBlank()) {
            log.error("Permanent indexing error for documentId={}: filename is missing or blank (no retry).", documentId);
            markFailed(documentId, "Indexing message filename is missing or blank");
            return;
        }

        int textLength = message.getExtractedText() != null ? message.getExtractedText().length() : 0;
        log.info("Received indexing message: documentId={}, filename={}, textLength={}",
                documentId, message.getFilename(), textLength);

        if (statusPublisher != null) {
            statusPublisher.publishInProgress(documentId);
        }

        LocalDateTime uploadedAt = resolveUploadedAt(message);

        int maxAttempts = Math.max(1, properties != null ? properties.getIndexingMaxRetries() : 3);
        long backoffMs = Math.max(0L, properties != null ? properties.getIndexingRetryBackoffMs() : 500L);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Reihenfolge im Erfolgsfall: Erst in Elasticsearch indexieren, dann DONE melden
                documentIndexService.indexDocument(
                        documentId,
                        message.getFilename(),
                        message.getExtractedText() != null ? message.getExtractedText() : "",
                        uploadedAt
                );

                if (statusPublisher != null) {
                    statusPublisher.publishDone(documentId);
                }
                log.info("Successfully indexed documentId={} and published indexStatus=DONE event.", documentId);
                return;
            } catch (IndexingPermanentException ex) {
                log.error("Permanent indexing failure for documentId={} (no retry): {}",
                        documentId, ex.getMessage());
                markFailed(documentId, ex.getMessage());
                return;
            } catch (IndexingTransientException ex) {
                log.warn("Transient indexing error on attempt {}/{} for documentId={}: {}",
                        attempt, maxAttempts, documentId, ex.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Exhausted all {} retry attempts for documentId={}; marking indexStatus=FAILED and sending to DLQ.",
                            maxAttempts, documentId, ex);
                    markFailed(documentId, ex.getMessage());
                    if (deadLetterPublisher != null) {
                        deadLetterPublisher.publishToDeadLetterQueue(message);
                    }
                    return;
                }
                sleepBackoff(backoffMs * (1L << (attempt - 1)), documentId);
            } catch (Exception ex) {
                log.error("Unexpected error while indexing documentId={}", documentId, ex);
                markFailed(documentId, ex.getMessage());
                return;
            }
        }
    }

    private void handleDelete(IndexingMessage message) {
        Long documentId = message.getDocumentId();
        log.info("Received delete indexing event for documentId={}", documentId);

        int maxAttempts = Math.max(1, properties != null ? properties.getIndexingMaxRetries() : 3);
        long backoffMs = Math.max(0L, properties != null ? properties.getIndexingRetryBackoffMs() : 500L);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                documentIndexService.deleteDocument(documentId);
                return;
            } catch (IndexingPermanentException ex) {
                log.error("Permanent failure deleting documentId={} from Elasticsearch (no retry): {}",
                        documentId, ex.getMessage());
                return;
            } catch (IndexingTransientException ex) {
                log.warn("Transient error deleting documentId={} from Elasticsearch on attempt {}/{}: {}",
                        documentId, attempt, maxAttempts, ex.getMessage());
                if (attempt == maxAttempts) {
                    log.error("Exhausted all {} retry attempts deleting documentId={} from Elasticsearch; sending to DLQ.",
                            maxAttempts, documentId, ex);
                    if (deadLetterPublisher != null) {
                        deadLetterPublisher.publishToDeadLetterQueue(message);
                    }
                    return;
                }
                sleepBackoff(backoffMs * (1L << (attempt - 1)), documentId);
            } catch (Exception ex) {
                log.error("Unexpected error deleting documentId={} from Elasticsearch", documentId, ex);
                return;
            }
        }
    }

    private LocalDateTime resolveUploadedAt(IndexingMessage message) {
        if (message.getUploadedAt() != null) {
            return message.getUploadedAt();
        }
        if (message.getProcessedAt() != null) {
            return message.getProcessedAt();
        }
        return LocalDateTime.now();
    }

    private void markFailed(Long documentId, String errorMessage) {
        if (statusPublisher != null) {
            String safeMessage = (errorMessage != null && !errorMessage.isBlank())
                    ? errorMessage
                    : "Indexing failed";
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
            throw new IndexingTransientException("Interrupted during retry backoff for documentId=" + documentId, ie);
        }
    }
}
