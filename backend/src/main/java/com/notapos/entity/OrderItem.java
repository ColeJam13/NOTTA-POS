package com.notapos.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a single item within an order.
 * 
 * Each item has a 15-second delay before being locked and sent to kitchen.
 * Servers can edit/delete items during the delay window.
 * 
 * @author CJ
 */

@Entity
@Table(name = "order_items")
public class OrderItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "draft";

    @Column(name = "delay_seconds", nullable = false)
    private Integer delaySeconds = 15;

    @Column(name = "delay_expires_at")
    private LocalDateTime delayExpiresAt;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "fired_at")
    private LocalDateTime firedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public OrderItem() {}

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getSpecialInstructions() {
        return specialInstructions;
    }

    public void setSpecialInstructions(String specialInstructions) {
        this.specialInstructions = specialInstructions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(Integer delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public LocalDateTime getDelayExpiresAt() {
        return delayExpiresAt;
    }

    public void setDelayExpiresAt(LocalDateTime delayExpiresAt) {
        this.delayExpiresAt = delayExpiresAt;
    }

    public Boolean getIsLocked() {
        return isLocked;
    }

    public void setIsLocked(Boolean isLocked) {
        this.isLocked = isLocked;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getFiredAt() {
        return firedAt;
    }

    public void setFiredAt(LocalDateTime firedAt) {
        this.firedAt = firedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }


}
