package cl.duoc.p360.orders.msg;

import cl.duoc.p360.orders.config.MessagingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica COMANDOS (tareas de trabajo) a RabbitMQ.
 * Exchanges y routing keys se leen de MessagingProperties (application.yml).
 */
@Component
public class CommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommandPublisher.class);

    private final RabbitTemplate rabbit;
    private final MessagingProperties props;

    public CommandPublisher(RabbitTemplate rabbit, MessagingProperties props) {
        this.rabbit = rabbit;
        this.props = props;
    }

    /** Envio exacto por routing key del caso de uso (ej: email.send / kitchen.ticket / invoice.gen). */
    public void sendDirect(CommandType type, EventEnvelope envelope) {
        publish(props.exchanges().direct(), route(type).routingKey(), envelope);
    }

    /** Variante por patron: la routing key del caso mas un sufijo (ej: email.send + "high" = email.send.high). */
    public void sendTopic(CommandType type, String variant, EventEnvelope envelope) {
        publish(props.exchanges().topic(), route(type).routingKey() + "." + variant, envelope);
    }

    private MessagingProperties.Route route(CommandType type) {
        return switch (type) {
            case EMAIL -> props.email();
            case KITCHEN -> props.kitchen();
            case INVOICE -> props.invoice();
        };
    }

    private void publish(String exchange, String routingKey, EventEnvelope envelope) {
        rabbit.convertAndSend(exchange, routingKey, envelope, m -> {
            // messageId permite idempotencia en el consumidor
            m.getMessageProperties().setMessageId(envelope.eventId());
            m.getMessageProperties().setCorrelationId(envelope.correlationId());
            return m;
        });
        log.info("[RabbitMQ] exchange={} rk={} type={} corr={}", exchange, routingKey, envelope.type(), envelope.correlationId());
    }
}
