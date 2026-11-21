package com.notapos.controller;

import com.notapos.dto.OrderItemModifierView;
import com.notapos.entity.OrderItemModifier;
import com.notapos.service.OrderItemModifierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for OrderItemModifier operations.
 * 
 * Exposes HTTP endpoints for managing order item modifiers.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/order-item-modifiers")
public class OrderItemModifierController {
    
    private final OrderItemModifierService orderItemModifierService;

    @Autowired
    public OrderItemModifierController(OrderItemModifierService orderItemModifierService) {
        this.orderItemModifierService = orderItemModifierService;
    }

    @GetMapping
    public ResponseEntity<List<OrderItemModifier>> getAllOrderItemModifiers() {                             // Get all order item modifiers
        return ResponseEntity.ok(orderItemModifierService.getAllOrderItemModifiers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderItemModifier> getOrderItemModifierById(@PathVariable Long id) {              // Get order item modifier by ID
        return orderItemModifierService.getOrderItemModifierById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order-item/{orderItemId}")
    public ResponseEntity<List<OrderItemModifier>> getModifiersForOrderItem(                                // Get modifiers for order item
            @PathVariable Long orderItemId) {
        return ResponseEntity.ok(orderItemModifierService.getModifiersForOrderItem(orderItemId));
    }

    @GetMapping("/modifier/{modifierId}")
    public ResponseEntity<List<OrderItemModifier>> getOrderItemsWithModifier(                               // Get order items with modifier
            @PathVariable Long modifierId) {
        return ResponseEntity.ok(orderItemModifierService.getOrderItemsWithModifier(modifierId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<OrderItemModifierView>> getModifiersForOrder(                                // Get modifiers (with names) for every item on an order
            @PathVariable Long orderId) {
        return ResponseEntity.ok(orderItemModifierService.getModifierViewsForOrder(orderId));
    }

    // Managers only, and no screen calls it. This adds a modifier to an item directly, with
    // none of the checks ringing in has (is the check open, is the item locked, does the
    // menu allow it) and without changing the item's price. Servers change modifiers through
    // PUT /api/order-items/{id}/modifiers, which has all of them.
    @PreAuthorize(Permissions.MANAGER_ONLY)
    @PostMapping
    public ResponseEntity<OrderItemModifier> createOrderItemModifier(                                       // Create new order item modifier
            @RequestBody OrderItemModifier orderItemModifier) {
        OrderItemModifier created = orderItemModifierService.createOrderItemModifier(orderItemModifier);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Managers only, for the same reason as the POST above
    @PreAuthorize(Permissions.MANAGER_ONLY)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrderItemModifier(@PathVariable Long id) {                            // Delete Existing order item modifier
        orderItemModifierService.deleteOrderItemModifier(id);
        return ResponseEntity.noContent().build();
    }
}
