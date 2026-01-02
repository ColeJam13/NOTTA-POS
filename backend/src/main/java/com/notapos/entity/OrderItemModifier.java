package com.notapos.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a modifier selected for an order item.
 * 
 * This tracks which modifiers the customer chose for their order.
 * Example: OrderItem "Chicken Cutty" has modifiers "Fries" and "Extra Sauce +$1"
 * 
 * @author CJ
 */

@Entity
@Table(name = "order_item_modifiers")
public class OrderItemModifier {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_modifier_id")
    private Long orderItemModifierId;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(name = "modifier_id", nullable = false)
    private Long modifierId;

    @Column(name = "price_adjustment", precision = 10, scale = 2)
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public OrderItemModifier() {}

    public Long getOrderItemModifierId() {
        return orderItemModifierId;
    }

    public void setOrderItemModifierId(Long orderItemModifierId) {
        this.orderItemModifierId = orderItemModifierId;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getModifierId() {
        return modifierId;
    }

    public void setModifierId(Long modifierId) {
        this.modifierId = modifierId;
    }

    public BigDecimal getPriceAdjustment() {
        return priceAdjustment;
    }

    public void setPriceAdjustment(BigDecimal priceAdjustment) {
        this.priceAdjustment = priceAdjustment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
