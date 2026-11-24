package capa.genaiworker.service;

import capa.genaiworker.model.SummaryResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMqSummaryResultPublisher implements SummaryResultPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String summaryExchange;
    private final String summaryRoutingKey;

    public RabbitMqSummaryResultPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.summary.exchange:summary.result.exchange}") String summaryExchange,
            @Value("${capadoc.rabbitmq.summary.routingkey:summary.result.routingkey}") String summaryRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.summaryExchange = summaryExchange;
        this.summaryRoutingKey = summaryRoutingKey;
    }

    @Override
    public void publishSummaryResult(SummaryResultMessage message) {
        log.info("Publishing summary result for documentId={} (summaryLength={}) to exchange='{}'",
                message.getDocumentId(),
                message.getSummary() != null ? message.getSummary().length() : 0,
                summaryExchange);

        rabbitTemplate.convertAndSend(summaryExchange, summaryRoutingKey, message);

        log.info("Successfully published summary result for documentId={}", message.getDocumentId());
    }
}
