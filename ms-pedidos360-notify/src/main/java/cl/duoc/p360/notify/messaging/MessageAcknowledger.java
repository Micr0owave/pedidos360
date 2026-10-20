package cl.duoc.p360.notify.messaging;

import cl.duoc.p360.notify.config.MessagingProperties;
import cl.duoc.p360.notify.exception.InvalidMessageException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Confirmacion explicita de mensajes (acknowledge-mode: manual), compartida por todos los consumidores.
 *
 *  - Exito: basicAck.
 *  - Duplicado (messageId ya procesado): basicAck sin repetir el trabajo.
 *  - Error NO recuperable (InvalidMessageException): basicNack sin requeue -> DLQ.
 *  - Error recuperable: se republica con contador x-retry-count y se confirma el original;
 *    al agotar max-retries, basicNack sin requeue -> DLQ.
 * Todo mensaje que va a la DLQ queda registrado en el log con su motivo.
 */
@Component
public class MessageAcknowledger {

    public static final String RETRY_HEADER = "x-retry-count";

    private static final Logger log = LoggerFactory.getLogger(MessageAcknowledger.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;
    private final IdempotencyGuard idempotency;
    private final EventLog eventLog;

    public MessageAcknowledger(RabbitTemplate rabbitTemplate, MessagingProperties props,
                               IdempotencyGuard idempotency, EventLog eventLog) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
        this.idempotency = idempotency;
        this.eventLog = eventLog;
    }

    public void process(MessagingProperties.Route route, Object payload, Delivery d, Runnable work) throws IOException {
        if (idempotency.alreadyProcessed(d.messageId())) {
            log.warn("[IDEMPOTENCIA] messageId {} ya procesado en {}, se descarta", d.messageId(), route.queue());
            eventLog.record(route.queue(), "DUPLICADO", "messageId ya procesado", d.correlationId());
            d.channel().basicAck(d.tag(), false);
            return;
        }
        int attempts = d.retryCount() == null ? 0 : d.retryCount();
        try {
            work.run();
            idempotency.markProcessed(d.messageId());
            eventLog.record(route.queue(), "ACK", "procesado", d.correlationId());
            d.channel().basicAck(d.tag(), false);
        } catch (InvalidMessageException e) {
            log.error("[DLQ] mensaje invalido en {} -> {}. Motivo: {} | corr={} | contenido={}",
                    route.queue(), route.deadLetterQueue(), e.getMessage(), d.correlationId(), payload);
            eventLog.record(route.queue(), "DLQ_INVALIDO", e.getMessage(), d.correlationId());
            d.channel().basicNack(d.tag(), false, false);
        } catch (RuntimeException e) {
            if (attempts < props.maxRetries()) {
                log.warn("Error recuperable en {} (reintento {}/{}): {}",
                        route.queue(), attempts + 1, props.maxRetries(), e.getMessage());
                eventLog.record(route.queue(), "REINTENTO", (attempts + 1) + "/" + props.maxRetries() + ": " + e.getMessage(), d.correlationId());
                republish(route, payload, d, attempts + 1);
                d.channel().basicAck(d.tag(), false);
            } else {
                log.error("[DLQ] reintentos agotados en {} -> {}. Motivo: {} | corr={} | contenido={}",
                        route.queue(), route.deadLetterQueue(), e.getMessage(), d.correlationId(), payload);
                eventLog.record(route.queue(), "DLQ_AGOTADO", e.getMessage(), d.correlationId());
                d.channel().basicNack(d.tag(), false, false);
            }
        }
    }

    /** Vuelve a publicar en el exchange directo conservando messageId y correlationId. */
    private void republish(MessagingProperties.Route route, Object payload, Delivery d, int nextAttempt) {
        rabbitTemplate.convertAndSend(props.exchanges().direct(), route.routingKey(), payload, message -> {
            message.getMessageProperties().setHeader(RETRY_HEADER, nextAttempt);
            message.getMessageProperties().setMessageId(d.messageId());
            message.getMessageProperties().setCorrelationId(d.correlationId());
            return message;
        });
    }
}
