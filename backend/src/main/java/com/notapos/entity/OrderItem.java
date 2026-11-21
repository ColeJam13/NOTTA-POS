package com.notapos.entity;

import jakarta.validation.constraints.*;
import com.notapos.enums.OrderItemStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;

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

    /**
     * The order an order's items are listed in, everywhere: the order the server rang
     * them in.
     *
     * WHY NOT BY ID: the order screen saves a round of items with several requests at
     * the same moment, and IDs are handed out as the requests arrive. The network does
     * not keep them in order, so the item tapped first can get the highest ID. Sorted
     * by ID, a kitchen ticket could list the main before the starter.
     *
     * The position comes first. ID settles a tie, which keeps two items that claim the
     * same position (two terminals on one table, or orders from before this column
     * existed, which all hold 0) in the order they were saved, and means the list
     * never shuffles between one refresh and the next.
     *
     * A Comparator is a rule for putting two things in order; list.sort(...) and
     * stream.sorted(...) take one.
     */
    public static final Comparator<OrderItem> IN_RING_IN_ORDER =
            Comparator.comparing(OrderItem::getRingInPosition)
                    .thenComparing(OrderItem::getOrderItemId);
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long orderItemId;

    @NotNull(message = "An order item must belong to an order")
    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @NotNull(message = "An order item must reference a menu item")
    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price cannot be negative")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;

    // Where this item sits on its order, as the server entered it: 0 for the first item
    // rung in, 1 for the next. See IN_RING_IN_ORDER below for why this exists.
    @Column(name = "ring_in_position", nullable = false)
    private Integer ringInPosition = 0;

    @Column(name = "status", nullable = false, length = 20)
    private String status = OrderItemStatus.DRAFT.getValue();

    @Column(name = "delay_seconds", nullable = false)
    private Integer delaySeconds = 15;

    @Column(name = "delay_expires_at")
    private LocalDateTime delayExpiresAt;

    @Column(name = "is_locked", nullable = false)
    private Boolean isLocked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

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

    public Integer getRingInPosition() {
        return ringInPosition;
    }

    public void setRingInPosition(Integer ringInPosition) {
        this.ringInPosition = ringInPosition;
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

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
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
