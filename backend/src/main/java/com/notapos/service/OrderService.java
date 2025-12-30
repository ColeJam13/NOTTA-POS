package com.notapos.service;

import com.notapos.entity.Order;
import com.notapos.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.math.RoundingMode;

/**
 * Service layer for Order operations.
 * 
 * Orders represent the entire check/tab for a table.
 * They stay open throughout the meal - OrderItems handle the delay timer.
 * 
 * @author CJ
 */

@Service
public class OrderService {
    
    private final OrderRepository orderRepository;

    @Autowired
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> getAllOrders() {                 // Get all orders
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {      // Get order by ID
        return orderRepository.findById(id);
    }

    public List<Order> getOrdersByTable(Long tableId) {     // Get all orders by table
        return orderRepository.findByTableId(tableId);
    }

    public List<Order> getOpenOrdersByTable(Long tableId) {                 // Get open orders for a table
        return orderRepository.findByTableIdAndStatus(tableId, "open");
    }

    public List<Order> getOrdersByStatus(String status) {           // Get orders by status
        return orderRepository.findByStatus(status);
    }

    public Order createOrder(Order order) {                 // create new order
        order.setStatus("open");
        return orderRepository.save(order);
    }

    public Order updateOrderTotals(Long id, BigDecimal subtotal, BigDecimal taxRate) {          // Update order totals as we add items
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id)); 

        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setTotal(total);

        return orderRepository.save(order);
    }

    public Order completeOrder(Long id) {                                       // Complete (close out) an order
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        
        order.setStatus("completed");
        order.setCompletedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

    public List<Order> getOrdersBetween(LocalDateTime start, LocalDateTime end) {       // get all orders in a certain time frame
        return orderRepository.findOrdersBetween(start, end);
    }

    public void deleteOrder(Long id) {                          // delete an order
        orderRepository.deleteById(id);
    }
}
