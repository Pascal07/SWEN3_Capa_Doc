package at.capadocapi.service;

import at.capadocapi.model.dto.SummaryResultMessage;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import at.capadocapi.service.Interfaces.DocumentSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.rabbitmq.listener.simple.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class DocumentSummaryListener {

    private final DocumentSummaryService documentSummaryService;
    private final DocumentMessagePublisher messagePublisher;

    @RabbitListener(
            queues = "${capadoc.rabbitmq.summary.queue:summary.result.queue}",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
    )
    public void handleSummaryResult(SummaryResultMessage message) {
        if (message == null
                || message.getDocumentId() == null
                || message.getDocumentId() <= 0
                || message.getSummary() == null
                || message.getSummary().isBlank()) {
            log.error("Received invalid SummaryResultMessage (missing documentId or blank summary); routing to DLQ.");
            if (messagePublisher != null) {
                messagePublisher.publishInvalidSummaryResultToDlq(message);
            }
            return;
        }

        log.info("Received GenAI summary result for documentId={} (summaryLength={})",
                message.getDocumentId(), message.getSummary().length());

        try {
            documentSummaryService.saveSummary(message.getDocumentId(), message.getSummary());
        } catch (IllegalArgumentException ex) {
            log.error("Invalid SummaryResultMessage for documentId={}: {}; routing to DLQ.",
                    message.getDocumentId(), ex.getMessage());
            if (messagePublisher != null) {
                messagePublisher.publishInvalidSummaryResultToDlq(message);
            }
        }
    }
}
