package cl.duoc.p360.notify.service;

import cl.duoc.p360.notify.exception.InvalidMessageException;
import cl.duoc.p360.notify.exception.TransientProcessingException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Ayuda para la evidencia de la presentacion:
 *  - payload.forceError=true     -> error NO recuperable: NACK directo a la DLQ.
 *  - payload.forceTransient=true -> error recuperable: se reintenta y, al agotar reintentos, va a la DLQ.
 */
@Component
public class FailureSimulator {

    public void apply(Map<String, Object> envelope) {
        Object payload = envelope.get("payload");
        if (payload instanceof Map<?, ?> p) {
            if (Boolean.TRUE.equals(p.get("forceError"))) {
                throw new InvalidMessageException("Fallo forzado para demostrar el flujo de DLQ");
            }
            if (Boolean.TRUE.equals(p.get("forceTransient"))) {
                throw new TransientProcessingException("Fallo temporal forzado para demostrar reintentos");
            }
        }
    }
}
