package cl.duoc.p360.orders.domain;

public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String id) {
        super("Pedido no encontrado: " + id);
    }
}
