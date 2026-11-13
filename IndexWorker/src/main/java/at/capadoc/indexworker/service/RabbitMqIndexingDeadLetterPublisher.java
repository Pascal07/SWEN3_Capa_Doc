package at.capadoc.indexworker.service;

import at.capadoc.indexworker.model.IndexingMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMqIndexingDeadLetterPublisher implements IndexingDeadLetterPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String dlxExchange;
    private final String dlqRoutingKey;

    public RabbitMqIndexingDeadLetterPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.indexing.dlx:indexing.dlx}") String dlxExchange,
            @Value("${capadoc.rabbitmq.indexing.dlq.routingkey:indexing.dlq.routingkey}") String dlqRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.dlxExchange = dlxExchange;
        this.dlqRoutingKey = dlqRoutingKey;
    }

    @Override
    public void publishToDeadLetterQueue(IndexingMessage failedMessage) {
        try {
            log.warn("Forwarding failed indexing message for documentId={} to dead-letter exchange '{}'",
                    failedMessage != null ? failedMessage.getDocumentId() : null, dlxExchange);
            rabbitTemplate.convertAndSend(dlxExchange, dlqRoutingKey, failedMessage);
        } catch (AmqpException ex) {
            log.error("Failed to forward indexing message for documentId={} to dead-letter queue",
                    failedMessage != null ? failedMessage.getDocumentId() : null, ex);
        }
    }
}
