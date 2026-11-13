package at.capadocapi.service;

import at.capadocapi.model.dto.DocumentStatusEvent;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import at.capadocapi.service.Interfaces.DocumentStatusService;
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
public class DocumentStatusListener {

    private final DocumentStatusService documentStatusService;
    private final DocumentMessagePublisher messagePublisher;

    @RabbitListener(
            queues = "${capadoc.rabbitmq.status.queue:document.status}",
            autoStartup = "${spring.rabbitmq.listener.simple.auto-startup:true}"
    )
    public void handleDocumentStatusEvent(DocumentStatusEvent event) {
        if (event == null
                || event.getDocumentId() == null
                || event.getDocumentId() <= 0
                || event.getStage() == null
                || event.getStatus() == null) {
            log.error("Received invalid DocumentStatusEvent (missing or invalid required fields): {}; routing to DLQ.",
                    event);
            if (messagePublisher != null) {
                messagePublisher.publishInvalidStatusEventToDlq(event);
            }
            return;
        }

        try {
            documentStatusService.updateDocumentStatus(event);
        } catch (IllegalArgumentException ex) {
            log.error("Invalid DocumentStatusEvent for documentId={}: {}; routing to DLQ.",
                    event.getDocumentId(), ex.getMessage());
            if (messagePublisher != null) {
                messagePublisher.publishInvalidStatusEventToDlq(event);
            }
        }
    }
}
