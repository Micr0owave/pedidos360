package cl.duoc.p360.orders.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maquina de estados del pedido.
 * Regla clave del caso: no se puede "despachar" sin "aceptar".
 * Las transiciones validas estan declaradas aqui y se validan en OrderService.
 */
public enum OrderStatus {
    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        CREADO,         EnumSet.of(ACEPTADO, CANCELADO),
        ACEPTADO,       EnumSet.of(EN_PREPARACION, CANCELADO),
        EN_PREPARACION, EnumSet.of(DESPACHADO, CANCELADO),
        DESPACHADO,     EnumSet.of(ENTREGADO),
        ENTREGADO,      EnumSet.noneOf(OrderStatus.class),
        CANCELADO,      EnumSet.noneOf(OrderStatus.class)
    );

    public boolean canMoveTo(OrderStatus target) {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(OrderStatus.class)).contains(target);
    }

    public Set<OrderStatus> nextStates() {
        return ALLOWED.getOrDefault(this, EnumSet.noneOf(OrderStatus.class));
    }
}
