package capa.genaiworker.service;

import capa.genaiworker.exception.InvalidOcrTextException;
import capa.genaiworker.model.GenAiMessage;
import capa.genaiworker.model.SummaryResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class GenAiMessageListener {

    private final SummaryService summaryService;
    private final SummaryResultPublisher summaryResultPublisher;
    private final StatusPublisher statusPublisher;

    @Autowired
    public GenAiMessageListener(
            SummaryService summaryService,
            SummaryResultPublisher summaryResultPublisher,
            StatusPublisher statusPublisher) {
        this.summaryService = summaryService;
        this.summaryResultPublisher = summaryResultPublisher;
        this.statusPublisher = statusPublisher;
    }

    public GenAiMessageListener(
            SummaryService summaryService,
            SummaryResultPublisher summaryResultPublisher) {
        this(summaryService, summaryResultPublisher, null);
    }

    @RabbitListener(
            queues = "${capadoc.rabbitmq.genai.queue:genai.queue}",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
    )
    public void handleGenAiMessage(GenAiMessage message) {
        if (message == null || message.getDocumentId() == null || message.getDocumentId() <= 0) {
            log.error("Received invalid GenAI message without valid documentId; rejecting to DLQ.");
            throw new AmqpRejectAndDontRequeueException("Invalid GenAI message: missing or invalid documentId");
        }

        Long documentId = message.getDocumentId();
        int textLength = message.getExtractedText() != null ? message.getExtractedText().length() : 0;
        log.info("Received GenAI message for documentId={}, filename={}, textLength={}",
                documentId, message.getFilename(), textLength);

        if (statusPublisher != null) {
            statusPublisher.publishInProgress(documentId);
        }

        try {
            String summary = summaryService.generateSummary(documentId, message.getExtractedText());
            SummaryResultMessage result = SummaryResultMessage.builder()
                    .documentId(documentId)
                    .summary(summary)
                    .build();
            summaryResultPublisher.publishSummaryResult(result);
            if (statusPublisher != null) {
                statusPublisher.publishDone(documentId);
            }
            log.info("Completed GenAI summary workflow for documentId={}", documentId);
        } catch (InvalidOcrTextException ex) {
            log.warn("Invalid/empty OCR text for documentId={}: {}; rejecting to DLQ without calling Ollama.",
                    documentId, ex.getMessage());
            if (statusPublisher != null) {
                statusPublisher.publishFailed(documentId, ex.getMessage());
            }
            throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.error("Error generating summary for documentId={}: {}", documentId, ex.getMessage(), ex);
            if (statusPublisher != null) {
                statusPublisher.publishFailed(documentId, ex.getMessage());
            }
            throw ex;
        }
    }
}
