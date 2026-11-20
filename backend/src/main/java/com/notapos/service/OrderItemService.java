package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.enums.OrderItemStatus;
import com.notapos.enums.OrderStatus;
import java.util.ArrayList;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.exception.ItemLockedException;
import com.notapos.exception.OrderNotOpenException;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
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
    private final OrderRepository orderRepository;                             // Needed to check an order is still open before anything is sent on it

    @Autowired
    public OrderItemService(OrderItemRepository orderItemRepository,
                            OrderItemModifierRepository orderItemModifierRepository,
                            OrderRepository orderRepository) {
        this.orderItemRepository = orderItemRepository;
        this.orderItemModifierRepository = orderItemModifierRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Make sure an order exists and is still open, and hand it back.
     *
     * WHY: Two terminals can have the same table up. If one closes the check, the other
     *      still shows it as open until it reloads. Without this check, items rung in or
     *      sent from the stale screen attached to the closed order: they were never billed,
     *      and the kitchen never saw them, because Active Orders lists open orders only.
     *
     * Everything that adds to, sends, or changes an order's items calls this first
     * (ringing in and changing modifiers do so from OrderItemModifierService).
     *
     * @throws ResourceNotFoundException if there is no such order (404)
     * @throws OrderNotOpenException     if the order is closed (409, code ORDER_NOT_OPEN)
     */
    public Order requireOpenOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!OrderStatus.OPEN.matches(order.getStatus())) {
            throw new OrderNotOpenException("This check was already closed, most likely on another terminal. Nothing was added or sent.");
        }
        return order;
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
    public List<OrderItem> sendItemsForOrder(Long orderId) {                                            // Send items to kitchen/bar (STARTS THE TIMER WHEN SERVER HITS SEND)
        requireOpenOrder(orderId);                                 // Nothing more goes to the kitchen on a closed check

        List<OrderItem> draftItems = orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.DRAFT.getValue());

        if (draftItems.isEmpty()) {
            return draftItems;                                     // Nothing new to send: leave any running edit window alone
        }

        // Items sent a few seconds ago that are still counting down. The screen shows ONE
        // countdown for the whole order and restarts it when an item is added, so these
        // get the same fresh window as the new items (see restartEditWindow below).
        List<OrderItem> alreadyInLimbo = orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.LIMBO.getValue());

        LocalDateTime now = LocalDateTime.now();

        for (OrderItem item : draftItems) {
            item.setStatus(OrderItemStatus.LIMBO.getValue());
            item.setDelayExpiresAt(now.plusSeconds(item.getDelaySeconds() + 1));  // Add 1 extra second to compensate for frontend rendering
            item.setSentAt(now);
        }

        for (OrderItem item : alreadyInLimbo) {
            item.setDelayExpiresAt(now.plusSeconds(item.getDelaySeconds() + 1));
        }

        List<OrderItem> itemsToSave = new ArrayList<>(draftItems);
        itemsToSave.addAll(alreadyInLimbo);
        orderItemRepository.saveAll(itemsToSave);                  // One batched save instead of one save per item
        return draftItems;
    }

    /**
     * Give every item on an order that is still in its edit window a fresh window.
     *
     * WHY: The order screen shows one countdown for the whole order, and restarts it
     *      whenever the server adds, removes, or changes an item. The backend used to
     *      restart the window only for the one item that changed, so the other items
     *      locked at their original time while the screen still showed seconds left -
     *      and the server's next edit was refused. Now the backend and the screen agree:
     *      a change during the window restarts it for the whole order.
     *
     * Items that are already locked are not touched.
     */
    @Transactional
    public void restartEditWindow(Long orderId) {
        List<OrderItem> limboItems = orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.LIMBO.getValue());

        if (limboItems.isEmpty()) {
            return;                                                // No window is running on this order
        }

        LocalDateTime now = LocalDateTime.now();
        for (OrderItem item : limboItems) {
            item.setDelayExpiresAt(now.plusSeconds(item.getDelaySeconds() + 1));  // Same +1 second as sendItemsForOrder
        }

        orderItemRepository.saveAll(limboItems);
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
        requireOpenOrder(orderId);                                 // Nothing more goes to the kitchen on a closed check

        List<OrderItem> itemsToSend = new ArrayList<>();
        itemsToSend.addAll(orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.DRAFT.getValue()));
        itemsToSend.addAll(orderItemRepository.findByOrderIdAndStatus(orderId, OrderItemStatus.LIMBO.getValue()));

        LocalDateTime now = LocalDateTime.now();

        for (OrderItem item : itemsToSend) {
            item.setIsLocked(true);
            item.setStatus(OrderItemStatus.PENDING.getValue());
            // A draft sent straight to the kitchen never went through Send, so it has no send
            // time yet. The kitchen display needs one to show how long the ticket has waited.
            if (item.getSentAt() == null) {
                item.setSentAt(now);
            }
        }

        orderItemRepository.saveAll(itemsToSend);                  // One batched save for the whole order
        return itemsToSend;
    }

    @Transactional
    public OrderItem sendItemNow(Long id) {                             // Send order now (Bypass timer)
        OrderItem item = orderItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + id));

        requireOpenOrder(item.getOrderId());                       // Nothing more goes to the kitchen on a closed check

        item.setIsLocked(true);
        item.setStatus(OrderItemStatus.PENDING.getValue());
        if (item.getSentAt() == null) {
            item.setSentAt(LocalDateTime.now());                   // Same reason as in sendOrderNow
        }

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

        // Removing an item during the edit window restarts the window for the rest of the order
        if (OrderItemStatus.LIMBO.matches(item.getStatus())) {
            restartEditWindow(item.getOrderId());
        }
    }
}
