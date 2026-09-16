package cl.duoc.p360.orders.msg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica EVENTOS de negocio a Kafka.
 * orders.events es la "fuente de verdad" que consumen report y audit.
 * La key es el orderId -> garantiza orden por pedido dentro de una particion.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    public static final String TOPIC_ORDERS = "orders.events";
    public static final String TOPIC_AUDIT  = "audit.timeline";

    private final KafkaTemplate<String, Object> kafka;

    public EventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publishOrderEvent(String orderId, EventEnvelope envelope) {
        kafka.send(TOPIC_ORDERS, orderId, envelope);
        kafka.send(TOPIC_AUDIT, orderId, envelope);
        log.info("[Kafka] topic={} key={} type={}", TOPIC_ORDERS, orderId, envelope.type());
    }
}
