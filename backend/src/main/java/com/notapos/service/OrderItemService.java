package com.notapos.service;

import com.notapos.entity.OrderItem;
import com.notapos.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for OrderItem operations.
 * 
 * Contains all business logic for the 15-second delay timer feature.
 * 
 * @author CJ
 */

@Service
public class OrderItemService {
    
    private final OrderItemRepository orderItemRepository;

    @Autowired
    public OrderItemService(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    public List<OrderItem> getAllOrderItems() {                 // Get all order items
        return orderItemRepository.findAll();
    }

    public Optional<OrderItem> getOrderItemById(Long id) {      // Get order item by id
        return orderItemRepository.findById(id);
    }

    public List<OrderItem> getItemsByOrder(Long orderId) {      // Get all items for a specific order
        return orderItemRepository.findByOrderId(orderId);
    }

    public List<OrderItem> getDraftItemsByOrder(Long orderId) {                 // Get draft items for an order (not sent yet)
        return orderItemRepository.findByOrderIdAndStatus(orderId, "draft");
    }

    public List<OrderItem> getLimboItemsByOrder(Long orderId) {                     // Get items in limbo (timer active)
        return orderItemRepository.findByOrderIdAndStatus(orderId, "limbo");
    }

    public List<OrderItem> getPendingItemsByOrder(Long orderId) {               // Get pending items for an order 
        return orderItemRepository.findPendingItemsByOrder(orderId, "pending");
    }

    public List<OrderItem> getItemsByStatus(String status) {            // Get items by status
        return orderItemRepository.findByStatus(status);
    }

    public OrderItem createOrderItem(OrderItem orderItem) {             // Create a new order item (add item to order, starts as draft, no timer)
        orderItem.setStatus("draft");
        orderItem.setIsLocked(false);

        return orderItemRepository.save(orderItem);
    }

    public OrderItem updateOrderItem(Long id, OrderItem updatedItem) {          // Update order item (only if not locked)
        OrderItem existing = orderItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order item not found with id: " + id));

        if (existing.getIsLocked()) {
            throw new RuntimeException("Cannot update locked item. Item has been sent to kitchen.");
        }

        existing.setQuantity(updatedItem.getQuantity());
        existing.setPrice(updatedItem.getPrice());
        existing.setSpecialInstructions(updatedItem.getSpecialInstructions());

        if (existing.getStatus().equals("pending")) {
            existing.setDelayExpiresAt(LocalDateTime.now().plusSeconds(existing.getDelaySeconds()));
        }

        return orderItemRepository.save(existing);
    }

    public List<OrderItem> sendItemsForOrder(Long orderId) {                                            // Send items to kitchen/bar (STARTS THE TIMER WHEN SERVER HITS SEND)
        List<OrderItem> draftItems = orderItemRepository.findByOrderIdAndStatus(orderId, "draft");

        LocalDateTime now = LocalDateTime.now();

        for (OrderItem item : draftItems) {
            item.setStatus("limbo");
            item.setDelayExpiresAt(now.plusSeconds(item.getDelaySeconds()));
            item.setSentAt(now);
            orderItemRepository.save(item);
        }

        return draftItems;
    }

    public List<OrderItem> lockAndSendExpiredItems() {                                  // Lock and fire all items whos delay has expired
        List<OrderItem> expiredItems = orderItemRepository.findExpiredUnlockedItems(
                LocalDateTime.now(),
                false
        );

        LocalDateTime now = LocalDateTime.now();

        for (OrderItem item : expiredItems) {
            item.setIsLocked(true);
            item.setStatus("pending");
            orderItemRepository.save(item);
        }

        return expiredItems;
    }

    public OrderItem sendItemNow(Long id) {                             // Send order now (Bypass timer)
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order item not found with id: " + id));

        item.setIsLocked(true);
        item.setStatus("pending");

        return orderItemRepository.save(item);
    }

    public OrderItem startItem(Long id) {                                       // Start item (kitchen function - Pending ---> Fired)
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order item not found with id: " + id));
        
                item.setStatus("fired");
                item.setFiredAt(LocalDateTime.now());

                return orderItemRepository.save(item);
    }

    public OrderItem completeItem(Long id) {                            // Mark item as completed
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order item not found with id: " + id));

        item.setStatus("completed");
        item.setCompletedAt(LocalDateTime.now());

        return orderItemRepository.save(item);
    }

    public void deleteOrderItem(Long id) {                              // Delete an order item (only if not locked)
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order item not found with id: " + id));

        if (item.getIsLocked()) {
            throw new RuntimeException("Cannot delete locked item. Item has been sent to kitchen.");
        }

        orderItemRepository.deleteById(id);
    }
}
