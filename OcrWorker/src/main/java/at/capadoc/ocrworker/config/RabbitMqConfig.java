package at.capadoc.ocrworker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // 1. Dead-Letter-Exchange & Queue (für fehlerhafte OCR-Nachrichten)
    @Bean
    public DirectExchange deadLetterExchange(
            @Value("${capadoc.rabbitmq.dlx}") String dlxName) {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue deadLetterQueue(
            @Value("${capadoc.rabbitmq.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding dlqBinding(
            Queue deadLetterQueue,
            DirectExchange deadLetterExchange,
            @Value("${capadoc.rabbitmq.dlq.routingkey}") String dlqRoutingKey) {
        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(dlqRoutingKey);
    }

    // 2. OCR-Exchange & Queue
    @Bean
    public DirectExchange ocrExchange(
            @Value("${capadoc.rabbitmq.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue ocrQueue(
            @Value("${capadoc.rabbitmq.queue}") String queueName,
            @Value("${capadoc.rabbitmq.dlx}") String deadLetterExchange,
            @Value("${capadoc.rabbitmq.dlq.routingkey}") String deadLetterRoutingKey) {

        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Binding ocrBinding(
            Queue ocrQueue,
            DirectExchange ocrExchange,
            @Value("${capadoc.rabbitmq.routingkey}") String routingKey) {

        return BindingBuilder
                .bind(ocrQueue)
                .to(ocrExchange)
                .with(routingKey);
    }

    // 3. Indexing-Exchange & Queue (Ziel-Queue nach erfolgreicher OCR)
    @Bean
    public DirectExchange indexingExchange(
            @Value("${capadoc.rabbitmq.indexing.exchange:indexing.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue indexingQueue(
            @Value("${capadoc.rabbitmq.indexing.queue:indexing.queue}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    public Binding indexingBinding(
            Queue indexingQueue,
            DirectExchange indexingExchange,
            @Value("${capadoc.rabbitmq.indexing.routingkey:indexing.routingkey}") String routingKey) {
        return BindingBuilder
                .bind(indexingQueue)
                .to(indexingExchange)
                .with(routingKey);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory =
                new SimpleRabbitListenerContainerFactory();

        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);

        // Bei einem Fehler nicht endlos dieselbe Nachricht erneut zustellen.
        factory.setDefaultRequeueRejected(false);

        return factory;
    }
}
