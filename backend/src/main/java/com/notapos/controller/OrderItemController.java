package com.notapos.controller;

import com.notapos.entity.OrderItem;
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

    @Autowired
    public OrderItemController(OrderItemService orderItemService) {
        this.orderItemService = orderItemService;
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
            if (status.equals("draft")) {

                return ResponseEntity.ok(orderItemService.getDraftItemsByOrder(orderId));
            } else if (status.equals("pending")) {

                return ResponseEntity.ok(orderItemService.getPendingItemsByOrder(orderId));
            }
        }

        return ResponseEntity.ok(orderItemService.getItemsByOrder(orderId));
    }

    @PostMapping                                                                            // Create new order item, add to order
    public ResponseEntity<OrderItem> createOrderItem(@RequestBody OrderItem orderItem) {
        OrderItem created = orderItemService.createOrderItem(orderItem);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")                                            // Update Order Item
    public ResponseEntity<OrderItem> updateOrderItem(
            @PathVariable Long id,
            @RequestBody OrderItem orderItem) {
        try {
            OrderItem updated = orderItemService.updateOrderItem(id, orderItem);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/order/{orderId}/send")                                                       // Send items, start timer
    public ResponseEntity<List<OrderItem>> sendItemsForOrder(@PathVariable Long orderId) {
        List<OrderItem> sentItems = orderItemService.sendItemsForOrder(orderId);
        return ResponseEntity.ok(sentItems);
    }

    @PostMapping("/order/{orderId}/send-now")                                                   // Ensures items appear as pending when timer is bypassed
    public ResponseEntity<List<OrderItem>> sendOrderNow(@PathVariable Long orderId) {
        List<OrderItem> draftItems = orderItemService.getDraftItemsByOrder(orderId);
        List<OrderItem> pendingItems = orderItemService.getPendingItemsByOrder(orderId);

        List<OrderItem> sentItems = new java.util.ArrayList<>();

        for (OrderItem item : draftItems) {
            sentItems.add(orderItemService.sendItemNow(item.getOrderItemId()));
        }

        for (OrderItem item : pendingItems) {
            sentItems.add(orderItemService.sendItemNow(item.getOrderItemId()));
        }

        return ResponseEntity.ok(sentItems);
    }

    @PutMapping("/{id}/send-now")                                                   // Send Item Immediately, bypass timer
    public ResponseEntity<OrderItem> sendItemNow(@PathVariable Long id) {
        try {
            OrderItem sent = orderItemService.sendItemNow(id);
            return ResponseEntity.ok(sent);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/start")                                                    // start item in kitchen
    public ResponseEntity<OrderItem> startItem(@PathVariable Long id) {
        try {
            OrderItem started = orderItemService.startItem(id);
            return ResponseEntity.ok(started);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/complete")                                               // Mark item as completed
    public ResponseEntity<OrderItem> completeItem(@PathVariable Long id) {
        try {
            OrderItem completed = orderItemService.completeItem(id);
            return ResponseEntity.ok(completed);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")                                                     // Delete order item
    public ResponseEntity<Void> deleteOrderItem(@PathVariable Long id) {
        try {
            orderItemService.deleteOrderItem(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/lock-expired")                                                       // Lock and fire items when delay timer hits zero
    public ResponseEntity<List<OrderItem>> lockExpiredItems() {
        List<OrderItem> locked = orderItemService.lockAndSendExpiredItems();
        return ResponseEntity.ok(locked);
    }
}
