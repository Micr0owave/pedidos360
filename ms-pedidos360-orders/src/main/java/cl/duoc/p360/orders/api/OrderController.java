package cl.duoc.p360.orders.api;

import cl.duoc.p360.orders.domain.Order;
import cl.duoc.p360.orders.domain.OrderStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Pedidos", description = "CRUD y cambios de estado de pedidos")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Lista pedidos. El CLIENTE solo ve los suyos; ADMIN y OPERADOR ven todos.")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','CLIENTE')")
    public List<Order> list(@AuthenticationPrincipal Jwt jwt) {
        if (hasRole(jwt, "ADMIN") || hasRole(jwt, "OPERADOR")) {
            return service.findAll();
        }
        return service.findByCustomer(subject(jwt));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un pedido")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR','CLIENTE')")
    public Order get(@PathVariable String id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un pedido en estado CREADO y emite OrderCreated")
    @PreAuthorize("hasAnyRole('CLIENTE','OPERADOR')")
    public Order create(@Valid @RequestBody Dtos.CreateOrderRequest req,
                        @AuthenticationPrincipal Jwt jwt) {
        return service.create(req, subject(jwt), claim(jwt, "preferred_username"));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambia el estado validando la maquina de estados")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public Order changeStatus(@PathVariable String id,
                              @Valid @RequestBody Dtos.ChangeStatusRequest req,
                              @AuthenticationPrincipal Jwt jwt) {
        return service.changeStatus(id, req.target(), req.reason(), subject(jwt));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancela el pedido (borrado logico via estado CANCELADO)")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public Order cancel(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return service.changeStatus(id, OrderStatus.CANCELADO, "cancelado por usuario", subject(jwt));
    }

    @GetMapping("/{id}/transitions")
    @Operation(summary = "Devuelve los estados a los que puede avanzar el pedido")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> transitions(@PathVariable String id) {
        Order o = service.findById(id);
        return ResponseEntity.ok(Map.of(
                "current", o.getStatus(),
                "allowed", o.getStatus().nextStates()));
    }

    private String subject(Jwt jwt) {
        if (jwt == null) return "anonimo";
        String oid = jwt.getClaimAsString("oid");
        return oid != null ? oid : jwt.getSubject();
    }

    private String claim(Jwt jwt, String name) {
        return jwt == null ? null : jwt.getClaimAsString(name);
    }

    private boolean hasRole(Jwt jwt, String role) {
        if (jwt == null) return false;
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.stream().anyMatch(r -> r.equalsIgnoreCase(role));
    }
}
