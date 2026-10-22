package at.capadocapi.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${capadoc.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${capadoc.rabbitmq.queue}")
    private String queueName;

    @Value("${capadoc.rabbitmq.routingkey}")
    private String routingKey;

    @Value("${capadoc.rabbitmq.dlx}")
    private String dlxName;

    @Value("${capadoc.rabbitmq.dlq}")
    private String dlqName;

    @Value("${capadoc.rabbitmq.dlq.routingkey}")
    private String dlqRoutingKey;

    // 1. Dead-Letter-Exchange & Queue (für fehlerhafte Nachrichten)
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding dlqBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(dlqRoutingKey);
    }

    // 2. Haupt-Exchange & Queue (verknüpft mit dem Dead-Letter-Exchange)
    @Bean
    public DirectExchange ocrExchange() {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue ocrQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding ocrBinding(Queue ocrQueue, DirectExchange ocrExchange) {
        return BindingBuilder.bind(ocrQueue).to(ocrExchange).with(routingKey);
    }

    // 3. JSON Converter (damit OcrMessage als JSON übertragen wird)
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}