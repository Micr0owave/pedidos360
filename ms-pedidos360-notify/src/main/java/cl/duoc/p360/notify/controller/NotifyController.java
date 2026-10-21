package cl.duoc.p360.notify.controller;

import cl.duoc.p360.notify.config.MessagingProperties;
import cl.duoc.p360.notify.messaging.EventLog;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Apoyo para la demostracion: ver lo que hizo el consumidor y disparar escenarios de prueba.
 * Solo se llega por el BFF, que lo restringe al rol ADMIN.
 */
@RestController
@RequestMapping("/api/notify")
public class NotifyController {

    /** queue: email | kitchen | invoice.  mode: ok | invalid | transient | duplicate. */
    public record TestRequest(String queue, String mode) { }

    private final EventLog eventLog;
    private final RabbitTemplate rabbit;
    private final MessagingProperties props;

    public NotifyController(EventLog eventLog, RabbitTemplate rabbit, MessagingProperties props) {
        this.eventLog = eventLog;
        this.rabbit = rabbit;
        this.props = props;
    }

    @GetMapping("/events")
    public List<EventLog.Entry> events() {
        return eventLog.recent();
    }

    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(@RequestBody TestRequest req) {
        MessagingProperties.Route route = switch (String.valueOf(req.queue())) {
            case "email" -> props.email();
            case "kitchen" -> props.kitchen();
            case "invoice" -> props.invoice();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "queue debe ser email, kitchen o invoice");
        };
        Map<String, Object> flags = switch (String.valueOf(req.mode())) {
            case "ok", "duplicate" -> Map.of();
            case "invalid" -> Map.of("forceError", true);
            case "transient" -> Map.of("forceTransient", true);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode debe ser ok, invalid, transient o duplicate");
        };

        String id = UUID.randomUUID().toString();
        Map<String, Object> envelope = Map.of(
                "type", "DemoCommand", "eventId", id, "timestamp", Instant.now().toString(),
                "traceId", UUID.randomUUID().toString(), "correlationId", "demo-" + id.substring(0, 8),
                "actor", "frontend", "payload", flags);

        int copies = "duplicate".equals(req.mode()) ? 2 : 1; // mismo messageId dos veces
        for (int i = 0; i < copies; i++) {
            rabbit.convertAndSend(props.exchanges().direct(), route.routingKey(), envelope, m -> {
                m.getMessageProperties().setMessageId(id);
                m.getMessageProperties().setCorrelationId((String) envelope.get("correlationId"));
                return m;
            });
        }
        return ResponseEntity.accepted().body(Map.of("queue", route.queue(), "mode", req.mode(), "sent", copies));
    }
}
