package cl.duoc.p360.mqadmin.controller;

import cl.duoc.p360.mqadmin.service.RabbitMonitorService;
import cl.duoc.p360.mqadmin.service.RabbitMonitorService.PeekedMessage;
import cl.duoc.p360.mqadmin.service.RabbitMonitorService.QueueSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta (solo lectura) del estado de RabbitMQ, para mostrarlo en el frontend. */
@Validated
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {

    private final RabbitMonitorService monitor;

    public MonitorController(RabbitMonitorService monitor) {
        this.monitor = monitor;
    }

    /** GET /api/monitor/queues -> todas las colas con mensajes, consumidores y si tienen DLQ configurada */
    @GetMapping("/queues")
    public List<QueueSummary> queues() {
        return monitor.queues();
    }

    /** GET /api/monitor/queues/{name}/messages?count=5 -> mira mensajes sin consumirlos */
    @GetMapping("/queues/{name}/messages")
    public List<PeekedMessage> messages(
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "nombre de cola invalido") String name,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int count) {
        return monitor.peek(name, count);
    }
}
