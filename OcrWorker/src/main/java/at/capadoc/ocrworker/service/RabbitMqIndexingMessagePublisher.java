package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.IndexingMessage;
import at.capadoc.ocrworker.model.OcrMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMqIndexingMessagePublisher implements IndexingMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String indexingExchange;
    private final String indexingRoutingKey;
    private final String dlxExchange;
    private final String dlqRoutingKey;

    public RabbitMqIndexingMessagePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.indexing.exchange:indexing.exchange}") String indexingExchange,
            @Value("${capadoc.rabbitmq.indexing.routingkey:indexing.routingkey}") String indexingRoutingKey,
            @Value("${capadoc.rabbitmq.dlx:ocr.dlx}") String dlxExchange,
            @Value("${capadoc.rabbitmq.dlq.routingkey:ocr.dlq.routingkey}") String dlqRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.indexingExchange = indexingExchange;
        this.indexingRoutingKey = indexingRoutingKey;
        this.dlxExchange = dlxExchange;
        this.dlqRoutingKey = dlqRoutingKey;
    }

    @Override
    public void publishIndexingMessage(IndexingMessage message) {
        log.info("Publishing indexing message for documentId={}, filename={}, textLength={}",
                message.getDocumentId(),
                message.getFilename(),
                message.getExtractedText() != null ? message.getExtractedText().length() : 0);

        rabbitTemplate.convertAndSend(indexingExchange, indexingRoutingKey, message);

        log.info("Successfully published indexing message to exchange='{}' with routingKey='{}' for documentId={}",
                indexingExchange, indexingRoutingKey, message.getDocumentId());
    }

    @Override
    public void publishToDeadLetterQueue(OcrMessage failedMessage) {
        try {
            log.warn("Forwarding failed OCR message for documentId={} to dead-letter exchange '{}'",
                    failedMessage.getDocumentId(), dlxExchange);
            rabbitTemplate.convertAndSend(dlxExchange, dlqRoutingKey, failedMessage);
        } catch (AmqpException ex) {
            log.error("Failed to forward OCR message for documentId={} to dead-letter queue",
                    failedMessage.getDocumentId(), ex);
        }
    }
}
