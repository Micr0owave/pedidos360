package cl.duoc.p360.notify.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Logica de negocio: tickets de cocina. */
@Service
public class KitchenService {

    private static final Logger log = LoggerFactory.getLogger(KitchenService.class);
    private final FailureSimulator failures;

    public KitchenService(FailureSimulator failures) { this.failures = failures; }

    public void printTicket(Map<String, Object> envelope) {
        log.info("[COCINA] ticket type={} corr={}", envelope.get("type"), envelope.get("correlationId"));
        failures.apply(envelope);
    }
}
