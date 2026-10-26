package at.capadoc.ocrworker.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitMqConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

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