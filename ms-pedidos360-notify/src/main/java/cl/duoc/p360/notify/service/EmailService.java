package cl.duoc.p360.notify.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Logica de negocio: envio de correos. Aqui iria JavaMailSender / push real. */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final FailureSimulator failures;

    public EmailService(FailureSimulator failures) { this.failures = failures; }

    public void send(Map<String, Object> envelope) {
        log.info("[EMAIL] type={} corr={} payload={}",
                envelope.get("type"), envelope.get("correlationId"), envelope.get("payload"));
        failures.apply(envelope);
    }
}
