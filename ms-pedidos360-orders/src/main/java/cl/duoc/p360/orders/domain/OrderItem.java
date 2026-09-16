package cl.duoc.p360.orders.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "P360_ORDER_ITEMS")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ORDER_ID")
    @JsonIgnore
    private Order order;

    @Column(nullable = false, length = 50)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Long unitPrice;

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public Order getOrder() { return order; }
    public void setOrder(Order v) { this.order = v; }
    public String getProductId() { return productId; }
    public void setProductId(String v) { this.productId = v; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer v) { this.quantity = v; }
    public Long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Long v) { this.unitPrice = v; }
}
