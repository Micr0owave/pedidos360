package cl.duoc.p360.mqadmin.service;

import cl.duoc.p360.mqadmin.config.AdminProperties;
import cl.duoc.p360.mqadmin.dto.BindingRequest;
import cl.duoc.p360.mqadmin.dto.DestinationKind;
import cl.duoc.p360.mqadmin.dto.ExchangeRequest;
import cl.duoc.p360.mqadmin.dto.QueueInfoResponse;
import cl.duoc.p360.mqadmin.dto.QueueRequest;
import cl.duoc.p360.mqadmin.exception.InvalidRequestException;
import cl.duoc.p360.mqadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

/**
 * Toda la lógica de administración (crear/eliminar colas, exchanges y bindings) vive aquí.
 * Los controladores solo llaman a estos métodos de alto nivel y no conocen RabbitMQ.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin rabbitAdmin;
    private final AdminProperties props;

    public RabbitAdminService(RabbitAdmin rabbitAdmin, AdminProperties props) {
        this.rabbitAdmin = rabbitAdmin;
        this.props = props;
    }

    // ---------------------------------------------------------------- Colas

    public QueueInfoResponse createQueue(QueueRequest req) {
        assertUsableName(req.name());
        boolean durable = req.durable() == null || req.durable();
        QueueBuilder builder = durable ? QueueBuilder.durable(req.name()) : QueueBuilder.nonDurable(req.name());
        if (Boolean.TRUE.equals(req.autoDelete())) {
            builder.autoDelete();
        }
        if (hasText(req.deadLetterExchange())) {
            builder.deadLetterExchange(req.deadLetterExchange());
        }
        if (hasText(req.deadLetterRoutingKey())) {
            builder.deadLetterRoutingKey(req.deadLetterRoutingKey());
        }
        if (req.messageTtlMs() != null) {
            builder.ttl(req.messageTtlMs());
        }
        Queue queue = builder.build();
        rabbitAdmin.declareQueue(queue);
        log.info("Cola creada: {}", req.name());
        return getQueue(req.name());
    }

    public QueueInfoResponse getQueue(String name) {
        var info = rabbitAdmin.getQueueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        return new QueueInfoResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    public void deleteQueue(String name) {
        assertUsableName(name);
        getQueue(name); // lanza 404 si no existe
        rabbitAdmin.deleteQueue(name);
        log.info("Cola eliminada: {}", name);
    }

    // ------------------------------------------------------------ Exchanges

    public void createExchange(ExchangeRequest req) {
        assertUsableName(req.name());
        boolean durable = req.durable() == null || req.durable();
        ExchangeBuilder builder = switch (req.type()) {
            case DIRECT -> ExchangeBuilder.directExchange(req.name());
            case TOPIC -> ExchangeBuilder.topicExchange(req.name());
            case FANOUT -> ExchangeBuilder.fanoutExchange(req.name());
            case HEADERS -> ExchangeBuilder.headersExchange(req.name());
        };
        builder.durable(durable);
        if (Boolean.TRUE.equals(req.autoDelete())) {
            builder.autoDelete();
        }
        Exchange exchange = builder.build();
        rabbitAdmin.declareExchange(exchange);
        log.info("Exchange creado: {} ({})", req.name(), req.type());
    }

    public void deleteExchange(String name) {
        assertUsableName(name);
        // RabbitMQ trata el borrado de un exchange inexistente como éxito (es idempotente).
        rabbitAdmin.deleteExchange(name);
        log.info("Exchange eliminado: {}", name);
    }

    // ------------------------------------------------------------- Bindings

    public void createBinding(BindingRequest req) {
        rabbitAdmin.declareBinding(toBinding(req));
        log.info("Binding creado: {} -> {} ({})", req.exchange(), req.destination(), key(req.routingKey()));
    }

    public void deleteBinding(BindingRequest req) {
        rabbitAdmin.removeBinding(toBinding(req));
        log.info("Binding eliminado: {} -> {} ({})", req.exchange(), req.destination(), key(req.routingKey()));
    }

    private Binding toBinding(BindingRequest req) {
        if (req.destinationType() == DestinationKind.QUEUE) {
            getQueue(req.destination()); // 404 si la cola destino no existe
        }
        DestinationType type = req.destinationType() == DestinationKind.QUEUE
                ? DestinationType.QUEUE : DestinationType.EXCHANGE;
        return new Binding(req.destination(), type, req.exchange(), key(req.routingKey()), null);
    }

    // ------------------------------------------------------------- Helpers

    private void assertUsableName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("El nombre es obligatorio");
        }
        if (name.length() > props.maxNameLength()) {
            throw new InvalidRequestException("El nombre supera el máximo de " + props.maxNameLength() + " caracteres");
        }
        if (name.startsWith(props.reservedPrefix())) {
            throw new InvalidRequestException("El prefijo '" + props.reservedPrefix() + "' está reservado por RabbitMQ");
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String key(String routingKey) {
        return routingKey == null ? "" : routingKey;
    }
}
