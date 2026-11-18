package com.notapos.controller;

import jakarta.validation.Valid;
import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.enums.OrderStatus;
import com.notapos.service.OrderCheckService;
import com.notapos.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * REST API Controller for Order operations.
 * 
 * Manages customer orders (checks/tabs for tables).
 * Orders stay open throughout the meal.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    private final OrderService orderService;
    private final OrderCheckService orderCheckService;

    @Autowired
    public OrderController(OrderService orderService, OrderCheckService orderCheckService) {
        this.orderService = orderService;
        this.orderCheckService = orderCheckService;
    }

@GetMapping
public ResponseEntity<List<Order>> getAllOrders(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long tableId) {  // ADD tableId param
    
    if (tableId != null) {  // ADD this block
        return ResponseEntity.ok(orderService.getOrdersByTable(tableId));
    }
    
    if (status != null) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status));
    }
    
    return ResponseEntity.ok(orderService.getAllOrders());
}

    @GetMapping("/{id}")                                                    // Get order by ID
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/check")                                              // Get the check for an order: subtotal, tax, total (worked out by the backend)
    public ResponseEntity<OrderCheckView> getOrderCheck(@PathVariable Long id) {
        return ResponseEntity.ok(orderCheckService.getCheck(id));
    }

    @GetMapping("/table/{tableId}")                                         // Get orders by table
    public ResponseEntity<List<Order>> getOrdersByTable(
            @PathVariable Long tableId,
            @RequestParam(required = false) String status) {

        if (OrderStatus.OPEN.matches(status)) {
            return ResponseEntity.ok(orderService.getOpenOrdersByTable(tableId));
        }
        return ResponseEntity.ok(orderService.getOrdersByTable(tableId));
    }

    @PostMapping                                                                // Create new order
    public ResponseEntity<Order> createOrder(@Valid @RequestBody Order order) {
        Order created = orderService.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/totals")                                                 // Update order Totals
    public ResponseEntity<Order> updateOrderTotals(
            @PathVariable Long id,
            @RequestParam BigDecimal subtotal,
            @RequestParam BigDecimal taxRate) {
        Order updated = orderService.updateOrderTotals(id, subtotal, taxRate);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/close")                                           // Mark order as closed
    public ResponseEntity<Order> closeOrder(@PathVariable Long id) {
        Order closed = orderService.closeOrder(id);
        return ResponseEntity.ok(closed);
    }

    @DeleteMapping("/{id}")                                                     // Clear an empty open order off its table (409 if it has items or is closed)
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
