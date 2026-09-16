package cl.duoc.p360.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SpringBootApplication
public class NotifyApplication {
    public static void main(String[] args) { SpringApplication.run(NotifyApplication.class, args); }
}

/**
 * Consumidor de comandos RabbitMQ.
 * - ACK explicito: si el metodo retorna sin excepcion, Spring hace ACK.
 *   Si lanza AmqpRejectAndDontRequeueException, va a la DLQ configurada en la cola.
 * - Idempotencia: se guarda el messageId ya procesado para no repetir efectos.
 */
@Component
class CommandConsumer {

    private static final Logger log = LoggerFactory.getLogger(CommandConsumer.class);

    /** En produccion esto seria Redis o una tabla; para el caso basta en memoria. */
    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    @RabbitListener(queues = "q.cmd.email")
    public void onEmail(Map<String, Object> envelope,
                        @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (!claim(messageId, "q.cmd.email")) return;
        log.info("[EMAIL] type={} corr={} payload={}",
                envelope.get("type"), envelope.get("correlationId"), envelope.get("payload"));
        // Aqui iria JavaMailSender / push real.
        simulateFailureIfRequested(envelope);
    }

    @RabbitListener(queues = "q.cmd.kitchen")
    public void onKitchen(Map<String, Object> envelope,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (!claim(messageId, "q.cmd.kitchen")) return;
        log.info("[COCINA] ticket type={} corr={}", envelope.get("type"), envelope.get("correlationId"));
        simulateFailureIfRequested(envelope);
    }

    @RabbitListener(queues = "q.cmd.invoice")
    public void onInvoice(Map<String, Object> envelope,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        if (!claim(messageId, "q.cmd.invoice")) return;
        log.info("[BOLETA] generando documento type={} corr={}",
                envelope.get("type"), envelope.get("correlationId"));
        simulateFailureIfRequested(envelope);
    }

    /** Consumidores de DLQ: solo registran, para poder mostrar la evidencia de DLQ. */
    @RabbitListener(queues = {"q.cmd.email.dlq", "q.cmd.kitchen.dlq", "q.cmd.invoice.dlq"})
    public void onDlq(Message message) {
        log.error("[DLQ] mensaje muerto en {} -> {}",
                message.getMessageProperties().getConsumerQueue(),
                new String(message.getBody()));
    }

    private boolean claim(String messageId, String queue) {
        if (messageId == null) return true;
        if (!processed.add(messageId)) {
            log.warn("[IDEMPOTENCIA] messageId {} ya procesado en {}, se descarta", messageId, queue);
            return false;
        }
        return true;
    }

    /**
     * Truco util para la evidencia: si el payload trae forceError=true,
     * el mensaje falla y termina en la DLQ. Asi puedes capturar la DLQ funcionando.
     */
    @SuppressWarnings("unchecked")
    private void simulateFailureIfRequested(Map<String, Object> envelope) {
        Object payload = envelope.get("payload");
        if (payload instanceof Map<?, ?> p && Boolean.TRUE.equals(p.get("forceError"))) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
                    "Fallo forzado para demostrar el flujo de DLQ");
        }
    }
}

@Configuration
class NotifyConfig {
    @Bean
    MessageConverter jsonConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        // notify no expone API publica; se deja abierto solo actuator
        http.csrf(c -> c.disable()).authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }
}
