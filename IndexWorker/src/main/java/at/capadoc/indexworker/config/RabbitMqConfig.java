package at.capadoc.indexworker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    // 1. Indexing Exchange & Queue (Empfängt Nachrichten vom OCR-Worker und Lösch-Events vom REST-Server)
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

    // 2. Dead-Letter-Exchange & Queue für fehlgeschlagene Indexing-Nachrichten
    @Bean
    public DirectExchange indexingDeadLetterExchange(
            @Value("${capadoc.rabbitmq.indexing.dlx:indexing.dlx}") String dlxName) {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue indexingDeadLetterQueue(
            @Value("${capadoc.rabbitmq.indexing.dlq:indexing.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding indexingDlqBinding(
            Queue indexingDeadLetterQueue,
            DirectExchange indexingDeadLetterExchange,
            @Value("${capadoc.rabbitmq.indexing.dlq.routingkey:indexing.dlq.routingkey}") String dlqRoutingKey) {
        return BindingBuilder
                .bind(indexingDeadLetterQueue)
                .to(indexingDeadLetterExchange)
                .with(dlqRoutingKey);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
