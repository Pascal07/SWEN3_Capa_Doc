package capa.genaiworker.service;

import capa.genaiworker.model.DocumentStatusEvent;
import capa.genaiworker.model.ProcessingStage;
import capa.genaiworker.model.ProcessingStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class RabbitMqStatusPublisher implements StatusPublisher {

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
    public void publishInProgress(Long documentId) {
        publish(documentId, ProcessingStatus.IN_PROGRESS, null);
    }

    @Override
    public void publishDone(Long documentId) {
        publish(documentId, ProcessingStatus.DONE, null);
    }

    @Override
    public void publishFailed(Long documentId, String errorMessage) {
        publish(documentId, ProcessingStatus.FAILED, errorMessage);
    }

    private void publish(Long documentId, ProcessingStatus status, String errorMessage) {
        if (documentId == null) {
            return;
        }
        DocumentStatusEvent event = DocumentStatusEvent.builder()
                .documentId(documentId)
                .stage(ProcessingStage.SUMMARY)
                .status(status)
                .errorMessage(errorMessage)
                .timestamp(Instant.now())
                .build();
        try {
            rabbitTemplate.convertAndSend(statusExchange, statusRoutingKey, event);
            log.info("Published status event: documentId={}, stage=SUMMARY, status={}", documentId, status);
        } catch (AmqpException ex) {
            log.error("Failed to publish SUMMARY status event ({}) for documentId={}", status, documentId, ex);
        }
    }
}
