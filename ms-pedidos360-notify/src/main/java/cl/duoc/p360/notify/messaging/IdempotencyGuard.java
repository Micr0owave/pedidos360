package cl.duoc.p360.notify.messaging;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Evita repetir efectos si RabbitMQ entrega dos veces el mismo mensaje.
 * Un messageId solo se marca como procesado cuando el trabajo termino bien,
 * asi un reintento tras un error recuperable no se descarta por error.
 * En produccion esto seria Redis o una tabla; para el caso basta en memoria.
 */
@Component
public class IdempotencyGuard {

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    public boolean alreadyProcessed(String messageId) {
        return messageId != null && processed.contains(messageId);
    }

    public void markProcessed(String messageId) {
        if (messageId != null) processed.add(messageId);
    }
}
