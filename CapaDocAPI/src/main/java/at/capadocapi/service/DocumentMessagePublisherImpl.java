package at.capadocapi.service;

import at.capadocapi.exception.QueuePublishException;
import at.capadocapi.model.dto.IndexingDeleteMessage;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentMessagePublisherImpl implements DocumentMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;
    private final String routingKey;
    private final String indexingExchangeName;
    private final String indexingRoutingKey;
    private final String statusDlxName;
    private final String statusDlqRoutingKey;

    @Autowired
    public DocumentMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.exchange}") String exchangeName,
            @Value("${capadoc.rabbitmq.routingkey}") String routingKey,
            @Value("${capadoc.rabbitmq.indexing.exchange:indexing.exchange}") String indexingExchangeName,
            @Value("${capadoc.rabbitmq.indexing.routingkey:indexing.routingkey}") String indexingRoutingKey,
            @Value("${capadoc.rabbitmq.status.dlx:document.status.dlx}") String statusDlxName,
            @Value("${capadoc.rabbitmq.status.dlq.routingkey:document.status.dlq.routingkey}") String statusDlqRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchangeName;
        this.routingKey = routingKey;
        this.indexingExchangeName = indexingExchangeName;
        this.indexingRoutingKey = indexingRoutingKey;
        this.statusDlxName = statusDlxName;
        this.statusDlqRoutingKey = statusDlqRoutingKey;
    }

    public DocumentMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            String exchangeName,
            String routingKey,
            String indexingExchangeName,
            String indexingRoutingKey) {
        this(
                rabbitTemplate,
                exchangeName,
                routingKey,
                indexingExchangeName,
                indexingRoutingKey,
                "document.status.dlx",
                "document.status.dlq.routingkey"
        );
    }

    public DocumentMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            String exchangeName,
            String routingKey) {
        this(
                rabbitTemplate,
                exchangeName,
                routingKey,
                "indexing.exchange",
                "indexing.routingkey",
                "document.status.dlx",
                "document.status.dlq.routingkey"
        );
    }

    @Override
    public void publishOcrMessage(OcrMessage message) {
        try {
            log.info("Sending OCR message to queue for documentId={}, storageKey={}, filename={}",
                    message.getDocumentId(), message.getStorageKey(), message.getFilename());
            rabbitTemplate.convertAndSend(exchangeName, routingKey, message);
            log.info("Successfully published OCR message for documentId={}", message.getDocumentId());
        } catch (AmqpException ex) {
            log.error("Failed to publish OCR message for documentId={}", message.getDocumentId(), ex);
            throw new QueuePublishException(
                    "Failed to send message to RabbitMQ for document " + message.getDocumentId(),
                    ex
            );
        }
    }

    @Override
    public void publishDeleteIndexMessage(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            IndexingDeleteMessage deleteMessage = IndexingDeleteMessage.of(documentId);
            log.info("Sending indexing DELETE event to queue for documentId={}", documentId);
            rabbitTemplate.convertAndSend(indexingExchangeName, indexingRoutingKey, deleteMessage);
            log.info("Successfully published indexing DELETE event for documentId={}", documentId);
        } catch (AmqpException ex) {
            log.error("Failed to publish indexing DELETE event for documentId={}", documentId, ex);
            throw new QueuePublishException(
                    "Failed to send delete message to RabbitMQ for document " + documentId,
                    ex
            );
        }
    }

    @Override
    public void publishInvalidStatusEventToDlq(Object invalidPayload) {
        try {
            log.warn("Forwarding invalid DocumentStatusEvent payload to dead-letter exchange '{}'", statusDlxName);
            rabbitTemplate.convertAndSend(statusDlxName, statusDlqRoutingKey,
                    invalidPayload != null ? invalidPayload : "null-status-event");
        } catch (AmqpException ex) {
            log.error("Failed to forward invalid DocumentStatusEvent payload to dead-letter queue", ex);
        }
    }
}
