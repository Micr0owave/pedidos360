package cl.duoc.p360.orders.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara la topologia por codigo ademas del definitions.json de infra.
 * Asi el proyecto funciona aunque levantes un RabbitMQ limpio.
 */
@Configuration
public class RabbitConfig {

    public static final String EX_DIRECT = "cmd.direct";
    public static final String EX_TOPIC  = "cmd.topic";
    public static final String EX_DLX    = "cmd.dead.dlx";

    @Bean DirectExchange cmdDirect() { return ExchangeBuilder.directExchange(EX_DIRECT).durable(true).build(); }
    @Bean TopicExchange  cmdTopic()  { return ExchangeBuilder.topicExchange(EX_TOPIC).durable(true).build(); }
    @Bean DirectExchange cmdDlx()    { return ExchangeBuilder.directExchange(EX_DLX).durable(true).build(); }

    private Queue main(String name, String dlqRoutingKey) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", EX_DLX)
                .withArgument("x-dead-letter-routing-key", dlqRoutingKey)
                .build();
    }

    @Bean Queue qEmail()   { return main("q.cmd.email",   "email.send"); }
    @Bean Queue qKitchen() { return main("q.cmd.kitchen", "kitchen.ticket"); }
    @Bean Queue qInvoice() { return main("q.cmd.invoice", "invoice.gen"); }

    @Bean Queue qEmailDlq()   { return QueueBuilder.durable("q.cmd.email.dlq").build(); }
    @Bean Queue qKitchenDlq() { return QueueBuilder.durable("q.cmd.kitchen.dlq").build(); }
    @Bean Queue qInvoiceDlq() { return QueueBuilder.durable("q.cmd.invoice.dlq").build(); }

    @Bean Binding bEmailDirect()   { return BindingBuilder.bind(qEmail()).to(cmdDirect()).with("email.send"); }
    @Bean Binding bKitchenDirect() { return BindingBuilder.bind(qKitchen()).to(cmdDirect()).with("kitchen.ticket"); }
    @Bean Binding bInvoiceDirect() { return BindingBuilder.bind(qInvoice()).to(cmdDirect()).with("invoice.gen"); }

    @Bean Binding bEmailTopic()   { return BindingBuilder.bind(qEmail()).to(cmdTopic()).with("email.*"); }
    @Bean Binding bKitchenTopic() { return BindingBuilder.bind(qKitchen()).to(cmdTopic()).with("kitchen.#"); }
    @Bean Binding bInvoiceTopic() { return BindingBuilder.bind(qInvoice()).to(cmdTopic()).with("invoice.*"); }

    @Bean Binding bEmailDlq()   { return BindingBuilder.bind(qEmailDlq()).to(cmdDlx()).with("email.send"); }
    @Bean Binding bKitchenDlq() { return BindingBuilder.bind(qKitchenDlq()).to(cmdDlx()).with("kitchen.ticket"); }
    @Bean Binding bInvoiceDlq() { return BindingBuilder.bind(qInvoiceDlq()).to(cmdDlx()).with("invoice.gen"); }

    @Bean MessageConverter jsonConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter conv) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        return t;
    }
}
