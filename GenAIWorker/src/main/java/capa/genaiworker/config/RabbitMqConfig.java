package capa.genaiworker.config;

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
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    // 1. GenAI-Auftrags-Queue + Dead-Letter-Queue
    @Bean
    public DirectExchange genAiDeadLetterExchange(
            @Value("${capadoc.rabbitmq.genai.dlx:genai.dlx}") String dlxName) {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue genAiDeadLetterQueue(
            @Value("${capadoc.rabbitmq.genai.dlq:genai.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding genAiDlqBinding(
            Queue genAiDeadLetterQueue,
            DirectExchange genAiDeadLetterExchange,
            @Value("${capadoc.rabbitmq.genai.dlq-routingkey:genai.dlq.routingkey}") String dlqRoutingKey) {
        return BindingBuilder.bind(genAiDeadLetterQueue).to(genAiDeadLetterExchange).with(dlqRoutingKey);
    }

    @Bean
    public DirectExchange genAiExchange(
            @Value("${capadoc.rabbitmq.genai.exchange:genai.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue genAiQueue(
            @Value("${capadoc.rabbitmq.genai.queue:genai.queue}") String queueName,
            @Value("${capadoc.rabbitmq.genai.dlx:genai.dlx}") String dlxName,
            @Value("${capadoc.rabbitmq.genai.dlq-routingkey:genai.dlq.routingkey}") String dlqRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding genAiBinding(
            Queue genAiQueue,
            DirectExchange genAiExchange,
            @Value("${capadoc.rabbitmq.genai.routingkey:genai.routingkey}") String routingKey) {
        return BindingBuilder.bind(genAiQueue).to(genAiExchange).with(routingKey);
    }

    // 2. Summary-Ergebnis-Queue + Dead-Letter-Queue (Rückkanal zum REST-Server)
    @Bean
    public DirectExchange summaryResultDeadLetterExchange(
            @Value("${capadoc.rabbitmq.summary.dlx:summary.result.dlx}") String dlxName) {
        return new DirectExchange(dlxName);
    }

    @Bean
    public Queue summaryResultDeadLetterQueue(
            @Value("${capadoc.rabbitmq.summary.dlq:summary.result.dlq}") String dlqName) {
        return QueueBuilder.durable(dlqName).build();
    }

    @Bean
    public Binding summaryResultDlqBinding(
            Queue summaryResultDeadLetterQueue,
            DirectExchange summaryResultDeadLetterExchange,
            @Value("${capadoc.rabbitmq.summary.dlq-routingkey:summary.result.dlq.routingkey}") String dlqRoutingKey) {
        return BindingBuilder.bind(summaryResultDeadLetterQueue).to(summaryResultDeadLetterExchange).with(dlqRoutingKey);
    }

    @Bean
    public DirectExchange summaryResultExchange(
            @Value("${capadoc.rabbitmq.summary.exchange:summary.result.exchange}") String exchangeName) {
        return new DirectExchange(exchangeName);
    }

    @Bean
    public Queue summaryResultQueue(
            @Value("${capadoc.rabbitmq.summary.queue:summary.result.queue}") String queueName,
            @Value("${capadoc.rabbitmq.summary.dlx:summary.result.dlx}") String dlxName,
            @Value("${capadoc.rabbitmq.summary.dlq-routingkey:summary.result.dlq.routingkey}") String dlqRoutingKey) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", dlxName)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean
    public Binding summaryResultBinding(
            Queue summaryResultQueue,
            DirectExchange summaryResultExchange,
            @Value("${capadoc.rabbitmq.summary.routingkey:summary.result.routingkey}") String routingKey) {
        return BindingBuilder.bind(summaryResultQueue).to(summaryResultExchange).with(routingKey);
    }

    // 3. JSON Converter (INFERRED TypePrecedence für modulübergreifende DTOs)
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

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            @Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean autoStartup) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAutoStartup(autoStartup);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
