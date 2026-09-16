package cl.duoc.p360.orders.msg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica COMANDOS (tareas de trabajo) a RabbitMQ.
 * Exchanges y routing keys segun la topologia del caso.
 */
@Component
public class CommandPublisher {

    private static final Logger log = LoggerFactory.getLogger(CommandPublisher.class);

    public static final String EX_DIRECT = "cmd.direct";
    public static final String EX_TOPIC  = "cmd.topic";

    private final RabbitTemplate rabbit;

    public CommandPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /** Envio exacto por routing key: email.send / kitchen.ticket / invoice.gen */
    public void sendDirect(String routingKey, EventEnvelope envelope) {
        rabbit.convertAndSend(EX_DIRECT, routingKey, envelope, m -> {
            // messageId permite idempotencia en el consumidor
            m.getMessageProperties().setMessageId(envelope.eventId());
            m.getMessageProperties().setCorrelationId(envelope.correlationId());
            return m;
        });
        log.info("[RabbitMQ] direct rk={} type={} corr={}", routingKey, envelope.type(), envelope.correlationId());
    }

    /** Variantes por patron: email.send.high / kitchen.ticket.thermal / invoice.gen.pdf */
    public void sendTopic(String routingKey, EventEnvelope envelope) {
        rabbit.convertAndSend(EX_TOPIC, routingKey, envelope, m -> {
            m.getMessageProperties().setMessageId(envelope.eventId());
            m.getMessageProperties().setCorrelationId(envelope.correlationId());
            return m;
        });
        log.info("[RabbitMQ] topic rk={} type={} corr={}", routingKey, envelope.type(), envelope.correlationId());
    }
}
