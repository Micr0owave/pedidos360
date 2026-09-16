package cl.duoc.p360.orders.api;

import cl.duoc.p360.orders.domain.*;
import cl.duoc.p360.orders.msg.CommandPublisher;
import cl.duoc.p360.orders.msg.EventEnvelope;
import cl.duoc.p360.orders.msg.EventPublisher;
import cl.duoc.p360.orders.repo.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class OrderService {

    private final OrderRepository repo;
    private final CommandPublisher commands;
    private final EventPublisher events;
    private final CatalogClient catalog;

    public OrderService(OrderRepository repo, CommandPublisher commands,
                        EventPublisher events, CatalogClient catalog) {
        this.repo = repo;
        this.commands = commands;
        this.events = events;
        this.catalog = catalog;
    }

    @Transactional(readOnly = true)
    public List<Order> findAll() { return repo.findAll(); }

    @Transactional(readOnly = true)
    public List<Order> findByCustomer(String customerId) {
        return repo.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Order findById(String id) {
        return repo.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional
    public Order create(Dtos.CreateOrderRequest req, String actorId, String actorEmail) {
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setCustomerId(actorId);
        order.setCustomerEmail(req.customerEmail() != null ? req.customerEmail() : actorEmail);
        order.setStatus(OrderStatus.CREADO);
        order.setCreatedAt(Instant.now());

        long total = 0L;
        for (Dtos.ItemRequest it : req.items()) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(it.productId());
            item.setQuantity(it.quantity());
            item.setUnitPrice(it.unitPrice());
            order.getItems().add(item);
            total += (long) it.quantity() * it.unitPrice();
        }
        order.setTotal(total);
        repo.save(order);

        // Evento de negocio -> Kafka (lo consumen report y audit)
        events.publishOrderEvent(order.getId(),
                EventEnvelope.of("OrderCreated", order.getId(), actorId, snapshot(order)));

        // Comando -> RabbitMQ: confirmar al cliente por email
        commands.sendDirect("email.send",
                EventEnvelope.of("SendOrderConfirmationEmail", order.getId(), actorId,
                        Map.of("orderId", order.getId(),
                               "to", String.valueOf(order.getCustomerEmail()),
                               "total", order.getTotal())));

        return order;
    }

    @Transactional
    public Order changeStatus(String id, OrderStatus target, String reason, String actorId) {
        Order order = findById(id);
        OrderStatus current = order.getStatus();

        // Aqui se hace cumplir la regla clave: no se puede despachar sin aceptar.
        if (!current.canMoveTo(target)) {
            throw new InvalidTransitionException(current, target);
        }

        order.setStatus(target);
        if (target == OrderStatus.ENTREGADO) {
            order.setDeliveredAt(Instant.now());
        }
        repo.save(order);

        events.publishOrderEvent(order.getId(),
                EventEnvelope.of(eventNameFor(target), order.getId(), actorId,
                        merge(snapshot(order), Map.of("from", current.name(), "reason", String.valueOf(reason)))));

        dispatchCommands(order, target, actorId);
        return order;
    }

    /** Efectos laterales asincronos por cada transicion (comandos a RabbitMQ). */
    private void dispatchCommands(Order order, OrderStatus target, String actorId) {
        String corr = order.getId();
        switch (target) {
            case ACEPTADO -> {
                // El stock decrece al aceptar (regla del caso)
                order.getItems().forEach(i -> catalog.decreaseStock(i.getProductId(), i.getQuantity()));
                commands.sendDirect("kitchen.ticket",
                        EventEnvelope.of("PrintKitchenTicket", corr, actorId, snapshot(order)));
                commands.sendDirect("invoice.gen",
                        EventEnvelope.of("GenerateInvoice", corr, actorId, snapshot(order)));
            }
            case EN_PREPARACION -> commands.sendTopic("kitchen.ticket.thermal",
                        EventEnvelope.of("PrintThermalTicket", corr, actorId, snapshot(order)));
            case DESPACHADO -> commands.sendTopic("email.send.high",
                        EventEnvelope.of("NotifyDispatch", corr, actorId,
                                Map.of("orderId", corr, "to", String.valueOf(order.getCustomerEmail()))));
            case ENTREGADO -> {
                commands.sendTopic("invoice.gen.pdf",
                        EventEnvelope.of("GenerateInvoicePdf", corr, actorId, snapshot(order)));
                commands.sendDirect("email.send",
                        EventEnvelope.of("NotifyDelivered", corr, actorId,
                                Map.of("orderId", corr, "to", String.valueOf(order.getCustomerEmail()))));
            }
            case CANCELADO -> commands.sendDirect("email.send",
                        EventEnvelope.of("NotifyCancelled", corr, actorId,
                                Map.of("orderId", corr, "to", String.valueOf(order.getCustomerEmail()))));
            default -> { }
        }
    }

    private String eventNameFor(OrderStatus s) {
        return switch (s) {
            case ACEPTADO -> "OrderAccepted";
            case EN_PREPARACION -> "OrderPreparing";
            case DESPACHADO -> "OrderDispatched";
            case ENTREGADO -> "OrderDelivered";
            case CANCELADO -> "OrderCancelled";
            case CREADO -> "OrderCreated";
        };
    }

    private Map<String, Object> snapshot(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getId());
        m.put("customerId", o.getCustomerId());
        m.put("customerEmail", o.getCustomerEmail());
        m.put("status", o.getStatus().name());
        m.put("total", o.getTotal());
        m.put("createdAt", o.getCreatedAt().toString());
        m.put("deliveredAt", o.getDeliveredAt() != null ? o.getDeliveredAt().toString() : null);
        m.put("items", o.getItems().stream().map(i -> Map.of(
                "productId", i.getProductId(),
                "quantity", i.getQuantity(),
                "unitPrice", i.getUnitPrice())).toList());
        return m;
    }

    private Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
        Map<String, Object> m = new LinkedHashMap<>(a);
        m.putAll(b);
        return m;
    }
}
