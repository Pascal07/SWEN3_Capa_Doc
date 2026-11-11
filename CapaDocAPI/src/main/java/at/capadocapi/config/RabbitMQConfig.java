package at.capadocapi.config;

import org.springframework.amqp.core.*;
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

    @Value("${capadoc.rabbitmq.indexing.exchange:indexing.exchange}")
    private String indexingExchangeName;

    @Value("${capadoc.rabbitmq.indexing.queue:indexing.queue}")
    private String indexingQueueName;

    @Value("${capadoc.rabbitmq.indexing.routingkey:indexing.routingkey}")
    private String indexingRoutingKey;

    @Value("${capadoc.rabbitmq.status.exchange:document.status.exchange}")
    private String statusExchangeName;

    @Value("${capadoc.rabbitmq.status.queue:document.status}")
    private String statusQueueName;

    @Value("${capadoc.rabbitmq.status.routingkey:document.status.routingkey}")
    private String statusRoutingKey;

    @Value("${capadoc.rabbitmq.status.dlx:document.status.dlx}")
    private String statusDlxName;

    @Value("${capadoc.rabbitmq.status.dlq:document.status.dlq}")
    private String statusDlqName;

    @Value("${capadoc.rabbitmq.status.dlq.routingkey:document.status.dlq.routingkey}")
    private String statusDlqRoutingKey;

    // 1. Dead-Letter-Exchange & Queue (für fehlerhafte OCR-Nachrichten)
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

    // 2b. Indexing-Exchange & Queue (für Indexierungs- und Lösch-Events)
    @Bean
    public DirectExchange indexingExchange() {
        return new DirectExchange(indexingExchangeName);
    }

    @Bean
    public Queue indexingQueue() {
        return QueueBuilder.durable(indexingQueueName).build();
    }

    @Bean
    public Binding indexingBinding(Queue indexingQueue, DirectExchange indexingExchange) {
        return BindingBuilder.bind(indexingQueue).to(indexingExchange).with(indexingRoutingKey);
    }

    // 2c. Status-Exchange, Queue & Dead-Letter-Queue (Empfängt Status-Updates der Worker)
    @Bean
    public DirectExchange statusDeadLetterExchange() {
        return new DirectExchange(statusDlxName);
    }

    @Bean
    public Queue statusDeadLetterQueue() {
        return QueueBuilder.durable(statusDlqName).build();
    }

    @Bean
    public Binding statusDlqBinding(Queue statusDeadLetterQueue, DirectExchange statusDeadLetterExchange) {
        return BindingBuilder.bind(statusDeadLetterQueue).to(statusDeadLetterExchange).with(statusDlqRoutingKey);
    }

    @Bean
    public DirectExchange statusExchange() {
        return new DirectExchange(statusExchangeName);
    }

    @Bean
    public Queue statusQueue() {
        return QueueBuilder.durable(statusQueueName)
                .withArgument("x-dead-letter-exchange", statusDlxName)
                .withArgument("x-dead-letter-routing-key", statusDlqRoutingKey)
                .build();
    }

    @Bean
    public Binding statusBinding(Queue statusQueue, DirectExchange statusExchange) {
        return BindingBuilder.bind(statusQueue).to(statusExchange).with(statusRoutingKey);
    }

    // 3. JSON Converter (mit INFERRED TypePrecedence für modulübergreifende DTO-Deserialisierung)
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
