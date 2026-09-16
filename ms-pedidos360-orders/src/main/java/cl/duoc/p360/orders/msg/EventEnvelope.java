package cl.duoc.p360.orders.msg;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Envelope comun exigido por el caso: type, eventId, timestamp, traceId, correlationId.
 * Se usa TANTO para comandos RabbitMQ como para eventos Kafka.
 */
public record EventEnvelope(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        String actor,
        Map<String, Object> payload
) {
    public static EventEnvelope of(String type, String correlationId, String actor, Map<String, Object> payload) {
        return new EventEnvelope(
                type,
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                UUID.randomUUID().toString(),
                correlationId,
                actor,
                payload
        );
    }
}
