package at.capadocapi.service;

import at.capadocapi.exception.QueuePublishException;
import at.capadocapi.model.dto.IndexingDeleteMessage;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentMessagePublisherImpl implements DocumentMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentMessagePublisherImpl.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final String indexingExchange;
    private final String indexingRoutingKey;

    @Autowired
    public DocumentMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.exchange}") String exchange,
            @Value("${capadoc.rabbitmq.routingkey}") String routingKey,
            @Value("${capadoc.rabbitmq.indexing.exchange:indexing.exchange}") String indexingExchange,
            @Value("${capadoc.rabbitmq.indexing.routingkey:indexing.routingkey}") String indexingRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.indexingExchange = indexingExchange;
        this.indexingRoutingKey = indexingRoutingKey;
    }

    public DocumentMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            String exchange,
            String routingKey) {
        this(rabbitTemplate, exchange, routingKey, "indexing.exchange", "indexing.routingkey");
    }

    @Override
    public void publishOcrMessage(OcrMessage message) {
        try {
            log.info(
                    "Sending OCR message to queue for documentId={}, storageKey={}, filename={}",
                    message.getDocumentId(),
                    message.getStorageKey(),
                    message.getFilename()
            );

            rabbitTemplate.convertAndSend(exchange, routingKey, message);

            log.info(
                    "Successfully published OCR message for documentId={}",
                    message.getDocumentId()
            );
        } catch (AmqpException ex) {
            log.error(
                    "Failed to publish OCR message for documentId={}",
                    message.getDocumentId(),
                    ex
            );

            throw new QueuePublishException(
                    "Failed to send message to RabbitMQ for document "
                            + message.getDocumentId(),
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
            log.info("Sending delete indexing event to queue for documentId={}", documentId);
            rabbitTemplate.convertAndSend(indexingExchange, indexingRoutingKey, deleteMessage);
            log.info("Successfully published delete indexing event for documentId={}", documentId);
        } catch (AmqpException ex) {
            log.error("Failed to publish delete indexing event for documentId={}", documentId, ex);
            throw new QueuePublishException(
                    "Failed to send delete message to RabbitMQ for document " + documentId,
                    ex
            );
        }
    }
}
