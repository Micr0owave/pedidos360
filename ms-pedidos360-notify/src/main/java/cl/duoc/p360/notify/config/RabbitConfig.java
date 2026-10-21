package cl.duoc.p360.notify.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ del consumidor. Es identica a la de orders (declarar es idempotente),
 * asi notify arranca aunque el broker este vacio. Solo configura: no tiene logica de negocio.
 */
@Configuration
public class RabbitConfig {

    private final MessagingProperties props;

    public RabbitConfig(MessagingProperties props) {
        this.props = props;
    }

    @Bean DirectExchange cmdDirect() { return ExchangeBuilder.directExchange(props.exchanges().direct()).durable(true).build(); }
    @Bean TopicExchange  cmdTopic()  { return ExchangeBuilder.topicExchange(props.exchanges().topic()).durable(true).build(); }
    @Bean DirectExchange cmdDlx()    { return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build(); }

    @Bean Queue qEmail()   { return mainQueue(props.email()); }
    @Bean Queue qKitchen() { return mainQueue(props.kitchen()); }
    @Bean Queue qInvoice() { return mainQueue(props.invoice()); }

    @Bean Queue qEmailDlq()   { return QueueBuilder.durable(props.email().deadLetterQueue()).build(); }
    @Bean Queue qKitchenDlq() { return QueueBuilder.durable(props.kitchen().deadLetterQueue()).build(); }
    @Bean Queue qInvoiceDlq() { return QueueBuilder.durable(props.invoice().deadLetterQueue()).build(); }

    @Bean Binding bEmailDirect()   { return BindingBuilder.bind(qEmail()).to(cmdDirect()).with(props.email().routingKey()); }
    @Bean Binding bKitchenDirect() { return BindingBuilder.bind(qKitchen()).to(cmdDirect()).with(props.kitchen().routingKey()); }
    @Bean Binding bInvoiceDirect() { return BindingBuilder.bind(qInvoice()).to(cmdDirect()).with(props.invoice().routingKey()); }

    @Bean Binding bEmailTopic()   { return BindingBuilder.bind(qEmail()).to(cmdTopic()).with(props.email().topicPattern()); }
    @Bean Binding bKitchenTopic() { return BindingBuilder.bind(qKitchen()).to(cmdTopic()).with(props.kitchen().topicPattern()); }
    @Bean Binding bInvoiceTopic() { return BindingBuilder.bind(qInvoice()).to(cmdTopic()).with(props.invoice().topicPattern()); }

    @Bean Binding bEmailDlq()   { return BindingBuilder.bind(qEmailDlq()).to(cmdDlx()).with(props.email().routingKey()); }
    @Bean Binding bKitchenDlq() { return BindingBuilder.bind(qKitchenDlq()).to(cmdDlx()).with(props.kitchen().routingKey()); }
    @Bean Binding bInvoiceDlq() { return BindingBuilder.bind(qInvoiceDlq()).to(cmdDlx()).with(props.invoice().routingKey()); }

    @Bean
    MessageConverter jsonConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true); // el listener recibe Map<String,Object>
        return converter;
    }

    private Queue mainQueue(MessagingProperties.Route route) {
        return QueueBuilder.durable(route.queue())
                .withArgument("x-dead-letter-exchange", props.exchanges().deadLetter())
                .withArgument("x-dead-letter-routing-key", route.routingKey())
                .build();
    }
}
