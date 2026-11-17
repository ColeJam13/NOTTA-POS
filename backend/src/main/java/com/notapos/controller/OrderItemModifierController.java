package com.notapos.controller;

import com.notapos.dto.OrderItemModifierView;
import com.notapos.entity.OrderItemModifier;
import com.notapos.service.OrderItemModifierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping
    public ResponseEntity<OrderItemModifier> createOrderItemModifier(                                       // Create new order item modifier
            @RequestBody OrderItemModifier orderItemModifier) {
        OrderItemModifier created = orderItemModifierService.createOrderItemModifier(orderItemModifier);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrderItemModifier(@PathVariable Long id) {                            // Delete Existing order item modifier
        orderItemModifierService.deleteOrderItemModifier(id);
        return ResponseEntity.noContent().build();
    }
}
