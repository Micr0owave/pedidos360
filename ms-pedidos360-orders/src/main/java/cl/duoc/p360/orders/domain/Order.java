package cl.duoc.p360.orders.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "P360_ORDERS")
public class Order {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 120)
    private String customerId;      // oid del usuario Azure AD

    @Column(length = 200)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant deliveredAt;    // se llena al pasar a ENTREGADO -> permite calcular lead time

    @Column(nullable = false)
    private Long total;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String v) { this.customerId = v; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String v) { this.customerEmail = v; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus v) { this.status = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant v) { this.deliveredAt = v; }
    public Long getTotal() { return total; }
    public void setTotal(Long v) { this.total = v; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> v) { this.items = v; }
}
