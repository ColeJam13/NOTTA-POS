package com.notapos.controller;

import jakarta.validation.Valid;
import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.enums.OrderStatus;
import com.notapos.security.StaffSession;
import com.notapos.service.OrderCheckService;
import com.notapos.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.security.Permissions;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
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

    @PreAuthorize(Permissions.TAKE_ORDERS)                                      // Anyone who takes orders
    @PostMapping                                                                // Create new order
    public ResponseEntity<Order> createOrder(
            @Valid @RequestBody Order order,
            @AuthenticationPrincipal StaffSession currentStaff) {            // The signed-in person (never null: nobody else gets this far)
        // The order belongs to whoever is signed in at the screen that opened it.
        // A name or staff ID sent in the request is ignored: otherwise a request could
        // open a check in someone else's name.
        order.setServerName(currentStaff.name());
        order.setStaffId(currentStaff.staffId());

        Order created = orderService.createOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize(Permissions.TAKE_ORDERS)                                      // Anyone who takes orders
    @DeleteMapping("/{id}")                                                     // Clear an empty open order off its table (409 if it has items or is closed)
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
