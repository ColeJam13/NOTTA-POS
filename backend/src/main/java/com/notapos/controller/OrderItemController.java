package com.notapos.controller;

import jakarta.validation.Valid;
import com.notapos.dto.OrderItemModifiersRequest;
import com.notapos.dto.OrderItemRequest;
import com.notapos.entity.OrderItem;
import com.notapos.enums.OrderItemStatus;
import com.notapos.service.OrderItemModifierService;
import com.notapos.service.OrderItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for OrderItem operations.
 * 
 * Exposes endpoints for managing order items and the delay timer feature.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/order-items")
public class OrderItemController {
    
    private final OrderItemService orderItemService;
    private final OrderItemModifierService orderItemModifierService;       // Rings in / changes items that carry modifiers and kitchen notes

    @Autowired
    public OrderItemController(OrderItemService orderItemService,
                               OrderItemModifierService orderItemModifierService) {
        this.orderItemService = orderItemService;
        this.orderItemModifierService = orderItemModifierService;
    }

    @GetMapping                                                             // Get all order items
    public ResponseEntity<List<OrderItem>> getAllOrderItems(
            @RequestParam(required = false) String status) {

        if (status != null) {
            return ResponseEntity.ok(orderItemService.getItemsByStatus(status));
        }
        return ResponseEntity.ok(orderItemService.getAllOrderItems());
    }

    @GetMapping("/{id}")                                                            // Get order item by ID
    public ResponseEntity<OrderItem> getOrderItemById(@PathVariable Long id) {
        return orderItemService.getOrderItemById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderId}")                                             // Get all items for an order
    public ResponseEntity<List<OrderItem>> getItemsByOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String status) {

        if (status != null) {
            if (OrderItemStatus.DRAFT.matches(status)) {

                return ResponseEntity.ok(orderItemService.getDraftItemsByOrder(orderId));
            } else if (OrderItemStatus.PENDING.matches(status)) {

                return ResponseEntity.ok(orderItemService.getPendingItemsByOrder(orderId));
            }
        }

        return ResponseEntity.ok(orderItemService.getItemsByOrder(orderId));
    }

    // Ring in one item, with its modifiers and kitchen note if it has any. This is the only
    // way to add an item to an order. The request has no price: the backend works it out
    // (a "price" sent anyway is ignored). See OrderItemModifierService.ringInItem for the rules.
    @PostMapping                                                                            // Create new order item, add to order
    public ResponseEntity<OrderItem> ringInItem(@Valid @RequestBody OrderItemRequest request) {
        OrderItem created = orderItemModifierService.ringInItem(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/modifiers")                                  // Replace an item's modifiers + kitchen note (only if not locked)
    public ResponseEntity<OrderItem> replaceModifiers(
            @PathVariable Long id,
            @Valid @RequestBody OrderItemModifiersRequest request) {
        OrderItem updated = orderItemModifierService.replaceModifiersForOrderItem(id, request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/order/{orderId}/send")                                                       // Send items, start timer
    public ResponseEntity<List<OrderItem>> sendItemsForOrder(@PathVariable Long orderId) {
        List<OrderItem> sentItems = orderItemService.sendItemsForOrder(orderId);
        return ResponseEntity.ok(sentItems);
    }

    @PostMapping("/order/{orderId}/send-now")                                                   // Send every draft + limbo item now, bypass timer
    public ResponseEntity<List<OrderItem>> sendOrderNow(@PathVariable Long orderId) {
        List<OrderItem> sentItems = orderItemService.sendOrderNow(orderId);
        return ResponseEntity.ok(sentItems);
    }

    @PutMapping("/{id}/send-now")                                                   // Send Item Immediately, bypass timer
    public ResponseEntity<OrderItem> sendItemNow(@PathVariable Long id) {
        OrderItem sent = orderItemService.sendItemNow(id);
        return ResponseEntity.ok(sent);
    }

    @PutMapping("/{id}/start")                                                    // start item in kitchen
    public ResponseEntity<OrderItem> startItem(@PathVariable Long id) {
        OrderItem started = orderItemService.startItem(id);
        return ResponseEntity.ok(started);
    }

    @PutMapping("/{id}/complete")                                               // Mark item as completed
    public ResponseEntity<OrderItem> completeItem(@PathVariable Long id) {
        OrderItem completed = orderItemService.completeItem(id);
        return ResponseEntity.ok(completed);
    }

    @DeleteMapping("/{id}")                                                     // Delete order item
    public ResponseEntity<Void> deleteOrderItem(@PathVariable Long id) {
        orderItemService.deleteOrderItem(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/lock-expired")                                                       // Lock and fire items when delay timer hits zero
    public ResponseEntity<List<OrderItem>> lockExpiredItems() {
        List<OrderItem> locked = orderItemService.lockAndSendExpiredItems();
        return ResponseEntity.ok(locked);
    }
}
