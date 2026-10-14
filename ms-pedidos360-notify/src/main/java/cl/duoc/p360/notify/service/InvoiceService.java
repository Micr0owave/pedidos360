package cl.duoc.p360.notify.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Logica de negocio: generacion de boletas/facturas. */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private final FailureSimulator failures;

    public InvoiceService(FailureSimulator failures) { this.failures = failures; }

    public void generate(Map<String, Object> envelope) {
        log.info("[BOLETA] generando documento type={} corr={}", envelope.get("type"), envelope.get("correlationId"));
        failures.apply(envelope);
    }
}
