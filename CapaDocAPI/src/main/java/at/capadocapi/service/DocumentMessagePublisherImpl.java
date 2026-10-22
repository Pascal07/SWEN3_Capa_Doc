package at.capadocapi.service;

import at.capadocapi.exception.QueuePublishException;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.service.Interfaces.DocumentMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DocumentMessagePublisherImpl implements DocumentMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(DocumentMessagePublisherImpl.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public DocumentMessagePublisherImpl(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.exchange}") String exchange,
            @Value("${capadoc.rabbitmq.routingkey}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public void publishOcrMessage(OcrMessage message) {
        try {
            log.info("Sending OCR message to queue for documentId={}, filename={}",
                    message.getDocumentId(), message.getFilename());
            rabbitTemplate.convertAndSend(exchange, routingKey, message);
            log.info("Successfully published OCR message for documentId={}", message.getDocumentId());
        } catch (AmqpException ex) {
            log.error("Failed to publish OCR message for documentId={}", message.getDocumentId(), ex);
            throw new QueuePublishException("Failed to send message to RabbitMQ for document " + message.getDocumentId(), ex);
        }
    }
}