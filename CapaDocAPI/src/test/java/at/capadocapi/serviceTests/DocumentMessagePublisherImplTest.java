package at.capadocapi.serviceTests;

import at.capadocapi.exception.QueuePublishException;
import at.capadocapi.model.dto.OcrMessage;
import at.capadocapi.service.DocumentMessagePublisherImpl;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DocumentMessagePublisherImplTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final DocumentMessagePublisherImpl publisher =
            new DocumentMessagePublisherImpl(rabbitTemplate, "documents.exchange", "documents.routing-key");

    @Test
    void publishOcrMessage_sendsMessageToConfiguredExchangeAndRoutingKey() {
        OcrMessage message = message();

        publisher.publishOcrMessage(message);

        verify(rabbitTemplate).convertAndSend("documents.exchange", "documents.routing-key", message);
    }

    @Test
    void publishOcrMessage_whenRabbitTemplateFails_wrapsException() {
        OcrMessage message = message();
        AmqpException cause = new AmqpException("broker unavailable");
        doThrow(cause).when(rabbitTemplate)
                .convertAndSend("documents.exchange", "documents.routing-key", message);

        assertThatThrownBy(() -> publisher.publishOcrMessage(message))
                .isInstanceOf(QueuePublishException.class)
                .hasMessageContaining("document 12")
                .hasCause(cause);
    }

    private OcrMessage message() {
        return new OcrMessage(12L, "report.pdf", "application/pdf", LocalDateTime.of(2026, 1, 1, 10, 0));
    }
}
