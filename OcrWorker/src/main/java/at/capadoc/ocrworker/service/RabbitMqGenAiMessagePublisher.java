package at.capadoc.ocrworker.service;

import at.capadoc.ocrworker.model.GenAiMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMqGenAiMessagePublisher implements GenAiMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String genAiExchange;
    private final String genAiRoutingKey;

    public RabbitMqGenAiMessagePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${capadoc.rabbitmq.genai.exchange:genai.exchange}") String genAiExchange,
            @Value("${capadoc.rabbitmq.genai.routingkey:genai.routingkey}") String genAiRoutingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.genAiExchange = genAiExchange;
        this.genAiRoutingKey = genAiRoutingKey;
    }

    @Override
    public void publishGenAiMessage(GenAiMessage message) {
        log.info("Publishing GenAI summary request for documentId={}, filename={}, textLength={}",
                message.getDocumentId(),
                message.getFilename(),
                message.getExtractedText() != null ? message.getExtractedText().length() : 0);

        rabbitTemplate.convertAndSend(genAiExchange, genAiRoutingKey, message);

        log.info("Successfully published GenAI message to exchange='{}' with routingKey='{}' for documentId={}",
                genAiExchange, genAiRoutingKey, message.getDocumentId());
    }
}
