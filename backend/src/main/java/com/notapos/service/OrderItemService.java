package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.enums.OrderItemStatus;
import java.util.ArrayList;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.exception.ItemLockedException;
import com.notapos.entity.OrderItem;
import com.notapos.repository.OrderItemModifierRepository;
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
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class OrderItemService {
    
    private final OrderItemRepository orderItemRepository;
    private final OrderItemModifierRepository orderItemModifierRepository;     // Needed so deleting an item also deletes its modifiers

    @Autowired
    public OrderItemService(OrderItemRepository orderItemRepository,
                            OrderItemModifierRepository orderItemModifierRepository) {
        this.orderItemRepository = orderItemRepository;
        this.orderItemModifierRepository = orderItemModifierRepository;
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
        return orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.DRAFT.getValue());
    }

    public List<OrderItem> getLimboItemsByOrder(Long orderId) {                     // Get items in limbo (timer active)
        return orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.LIMBO.getValue());
    }

    public List<OrderItem> getPendingItemsByOrder(Long orderId) {               // Get pending items for an order 
        return orderItemRepository.findPendingItemsByOrder(orderId, OrderItemStatus.PENDING.getValue());
    }

    public List<OrderItem> getItemsByStatus(String status) {            // Get items by status
        return orderItemRepository.findByStatus(status);
    }

    @Transactional
    public OrderItem createOrderItem(OrderItem orderItem) {             // Create a new order item (add item to order, starts as draft, no timer)
        orderItem.setStatus(OrderItemStatus.DRAFT.getValue());
        orderItem.setIsLocked(false);

        return orderItemRepository.save(orderItem);
    }

    @Transactional
    public OrderItem updateOrderItem(Long id, OrderItem updatedItem) {          // Update order item (only if not locked)
        OrderItem existing = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));

        if (existing.getIsLocked()) {
            throw new ItemLockedException("Cannot update locked item. Item has been sent to kitchen.");
        }

        existing.setQuantity(updatedItem.getQuantity());
        existing.setPrice(updatedItem.getPrice());
        existing.setSpecialInstructions(updatedItem.getSpecialInstructions());

        if (OrderItemStatus.PENDING.matches(existing.getStatus())) {
            existing.setDelayExpiresAt(LocalDateTime.now().plusSeconds(existing.getDelaySeconds()));
        }

        return orderItemRepository.save(existing);
    }

    @Transactional
    public List<OrderItem> sendItemsForOrder(Long orderId) {                                            // Send items to kitchen/bar (STARTS THE TIMER WHEN SERVER HITS SEND)
        List<OrderItem> draftItems = orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.DRAFT.getValue());

        LocalDateTime now = LocalDateTime.now();

        for (OrderItem item : draftItems) {
            item.setStatus(OrderItemStatus.LIMBO.getValue());
            item.setDelayExpiresAt(now.plusSeconds(item.getDelaySeconds() + 1));  // Add 1 extra second to compensate for frontend rendering
            item.setSentAt(now);
        }

        orderItemRepository.saveAll(draftItems);                   // One batched save instead of one save per item
        return draftItems;
    }

    @Transactional
    public List<OrderItem> lockAndSendExpiredItems() {                                  // Lock and fire all items whos delay has expired
        List<OrderItem> expiredItems = orderItemRepository.findExpiredUnlockedItems(
                LocalDateTime.now(),
                false
        );

        LocalDateTime now = LocalDateTime.now();

        for (OrderItem item : expiredItems) {
            item.setIsLocked(true);
            item.setStatus(OrderItemStatus.PENDING.getValue());
        }

        orderItemRepository.saveAll(expiredItems);                 // One batched save instead of one save per item
        return expiredItems;
    }

    @Transactional
    public List<OrderItem> sendOrderNow(Long orderId) {                 // Send every draft + limbo item on an order now (bypass timer) - all or nothing
        List<OrderItem> itemsToSend = new ArrayList<>();
        itemsToSend.addAll(orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.DRAFT.getValue()));
        itemsToSend.addAll(orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.LIMBO.getValue()));

        for (OrderItem item : itemsToSend) {
            item.setIsLocked(true);
            item.setStatus(OrderItemStatus.PENDING.getValue());
        }

        orderItemRepository.saveAll(itemsToSend);                  // One batched save for the whole order
        return itemsToSend;
    }

    @Transactional
    public OrderItem sendItemNow(Long id) {                             // Send order now (Bypass timer)
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));

        item.setIsLocked(true);
        item.setStatus(OrderItemStatus.PENDING.getValue());

        return orderItemRepository.save(item);
    }

    @Transactional
    public OrderItem startItem(Long id) {                                       // Start item (kitchen function - Pending ---> Fired)
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));
        
                item.setStatus(OrderItemStatus.FIRED.getValue());
                item.setFiredAt(LocalDateTime.now());

                return orderItemRepository.save(item);
    }

    @Transactional
    public OrderItem completeItem(Long id) {                            // Mark item as completed
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));

        item.setStatus(OrderItemStatus.COMPLETED.getValue());
        item.setCompletedAt(LocalDateTime.now());

        return orderItemRepository.save(item);
    }

    @Transactional
    public void deleteOrderItem(Long id) {                              // Delete an order item (only if not locked)
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));

        if (item.getIsLocked()) {
            throw new ItemLockedException("Cannot delete locked item. Item has been sent to kitchen.");
        }

        // Remove the item's modifiers first. Tables are linked by plain ID columns (no database
        // foreign keys), so nothing would clean these rows up automatically.
        orderItemModifierRepository.deleteByOrderItemId(id);
        orderItemRepository.deleteById(id);
    }
}
