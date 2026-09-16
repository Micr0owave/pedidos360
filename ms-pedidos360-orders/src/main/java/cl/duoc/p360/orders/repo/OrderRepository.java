package cl.duoc.p360.orders.repo;

import cl.duoc.p360.orders.domain.Order;
import cl.duoc.p360.orders.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
