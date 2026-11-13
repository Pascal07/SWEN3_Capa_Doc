package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.DocumentStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class RabbitMqStatusPublisher implements StatusPublisher {

    private static final int MAX_PUBLISH_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 100L;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 250;

    private final RabbitTemplate rabbitTemplate;
    private final String statusExchange;
    private final String statusRoutingKey;

    public RabbitMqStatusPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.status.exchange:document.status.exchange}") String statusExchange,
            @Value("${capadoc.rabbitmq.status.routingkey:document.status.routingkey}") String statusRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.statusExchange = statusExchange;
        this.statusRoutingKey = statusRoutingKey;
    }

    @Override
    public void publishStatus(DocumentStatusEvent event) {
        if (event == null || event.getDocumentId() == null || event.getStage() == null || event.getStatus() == null) {
            log.warn("Ignoring incomplete DocumentStatusEvent: {}", event);
            return;
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        if (event.getErrorMessage() != null && event.getErrorMessage().length() > MAX_ERROR_MESSAGE_LENGTH) {
            event.setErrorMessage(event.getErrorMessage().substring(0, MAX_ERROR_MESSAGE_LENGTH));
        }

        for (int attempt = 1; attempt <= MAX_PUBLISH_ATTEMPTS; attempt++) {
            try {
                rabbitTemplate.convertAndSend(statusExchange, statusRoutingKey, event);
                log.info("Published status event: documentId={}, stage={}, status={}",
                        event.getDocumentId(), event.getStage(), event.getStatus());
                return;
            } catch (AmqpException ex) {
                log.warn("Failed to publish status event on attempt {}/{} for documentId={}, stage={}, status={}: {}",
                        attempt, MAX_PUBLISH_ATTEMPTS, event.getDocumentId(), event.getStage(), event.getStatus(), ex.getMessage());
                if (attempt == MAX_PUBLISH_ATTEMPTS) {
                    log.error("Giving up publishing status event after {} attempts for documentId={}, stage={}, status={}",
                            MAX_PUBLISH_ATTEMPTS, event.getDocumentId(), event.getStage(), event.getStatus(), ex);
                    return;
                }
                sleepQuietly(INITIAL_BACKOFF_MS * (1L << (attempt - 1)));
            }
        }
    }

    private void sleepQuietly(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
