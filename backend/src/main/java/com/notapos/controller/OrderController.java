package com.notapos.controller;

import com.notapos.entity.Order;
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

    @Autowired
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders(
            @RequestParam(required = false) String status) {

        if (status != null) {
            return ResponseEntity.ok(orderService.getOrdersByStatus(status));
        }
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/table/{tableId}")
    public ResponseEntity<List<Order>> getOrdersByTable(
            @PathVariable Long tableId,
            @RequestParam(required = false) String status) {

        if (status != null && status.equals("open")) {
            return ResponseEntity.ok(orderService.getOpenOrdersByTable(tableId));
        }
        return ResponseEntity.ok(orderService.getOrdersByTable(tableId));
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Order order) {
        Order created = orderService.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/totals")
    public ResponseEntity<Order> updateOrderTotals(
            @PathVariable Long id,
            @RequestParam BigDecimal subtotal,
            @RequestParam BigDecimal taxRate) {
        try {
            Order updated = orderService.updateOrderTotals(id, subtotal, taxRate);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}/complete")
    public ResponseEntity<Order> completeOrder(@PathVariable Long id) {
        try {
            Order completed = orderService.completeOrder(id);
            return ResponseEntity.ok(completed);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
