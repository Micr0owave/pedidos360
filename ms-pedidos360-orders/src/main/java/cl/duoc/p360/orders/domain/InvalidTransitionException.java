package cl.duoc.p360.orders.domain;

public class InvalidTransitionException extends RuntimeException {
    private final OrderStatus from;
    private final OrderStatus to;

    public InvalidTransitionException(OrderStatus from, OrderStatus to) {
        super("Transicion invalida: " + from + " -> " + to +
              ". Estados permitidos desde " + from + ": " + from.nextStates());
        this.from = from;
        this.to = to;
    }

    public OrderStatus getFrom() { return from; }
    public OrderStatus getTo() { return to; }
}
