package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.enums.OrderStatus;
import com.notapos.enums.TableStatus;
import com.notapos.exception.OrderStateException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.TableRepository;
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
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final TableRepository tableRepository;              // Opening or clearing an order changes its table's status
    private final OrderItemRepository orderItemRepository;      // Needed to check an order is empty before clearing it

    @Autowired
    public OrderService(OrderRepository orderRepository,
                        TableRepository tableRepository,
                        OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.orderItemRepository = orderItemRepository;
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
        return orderRepository.findByTableIdAndStatus(tableId, OrderStatus.OPEN.getValue());
    }

    public List<Order> getOrdersByStatus(String status) {           // Get orders by status
        return orderRepository.findByStatus(status);
    }

    /**
     * Open an order on a table and mark the table occupied.
     *
     * Both happen in one transaction. (The screen used to mark the table occupied
     * with a second request; if that request failed, the Floor Map showed a table
     * with an open check as free.)
     *
     * A table has one open check at a time. If the table already has one - two
     * servers opened the same table within a few seconds of each other - that
     * order is returned instead of starting a second check, so both servers
     * ring items onto the same check.
     */
    @Transactional
    public Order createOrder(Order order) {                 // create new order
        RestaurantTable table = tableRepository.findById(order.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + order.getTableId()));

        List<Order> alreadyOpen = orderRepository.findByTableIdAndStatus(order.getTableId(), OrderStatus.OPEN.getValue());
        if (!alreadyOpen.isEmpty()) {
            return alreadyOpen.get(0);
        }

        order.setStatus(OrderStatus.OPEN.getValue());
        Order saved = orderRepository.save(order);

        table.setStatus(TableStatus.OCCUPIED.getValue());
        tableRepository.save(table);

        return saved;
    }

    @Transactional
    public Order updateOrderTotals(Long id, BigDecimal subtotal, BigDecimal taxRate) {          // Update order totals as we add items
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id)); 

        BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setTotal(total);

        return orderRepository.save(order);
    }

    @Transactional
    public Order closeOrder(Long id) {                                       // Close out an order
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        order.setStatus(OrderStatus.CLOSED.getValue());
        order.setClosedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public List<Order> getOrdersBetween(LocalDateTime start, LocalDateTime end) {       // get all orders in a certain time frame
        return orderRepository.findOrdersBetween(start, end);
    }

    /**
     * Clear an empty open order off its table.
     *
     * This is how a table gets freed when the guests leave before ordering, or
     * the server removes every item: there is nothing to pay, so there is no
     * payment to close the order. The order is deleted and the table goes back
     * to available.
     *
     * Only an OPEN order with NO items can be cleared. A closed order is part of
     * the sales history, and an order with items has to be paid (or its items
     * removed first), so both are refused with a 409.
     */
    @Transactional
    public void deleteOrder(Long id) {                          // delete an order
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));

        if (!OrderStatus.OPEN.matches(order.getStatus())) {
            throw new OrderStateException("Order #" + id + " is closed and cannot be removed.");
        }

        if (!orderItemRepository.findByOrderId(id).isEmpty()) {
            throw new OrderStateException("Order #" + id + " still has items on it. Remove them or close the check.");
        }

        orderRepository.deleteById(id);

        // Free the table, unless it somehow has another open order
        Optional<RestaurantTable> tableOpt = tableRepository.findById(order.getTableId());
        boolean hasOtherOpenOrder = !orderRepository
                .findByTableIdAndStatus(order.getTableId(), OrderStatus.OPEN.getValue()).isEmpty();
        if (tableOpt.isPresent() && !hasOtherOpenOrder) {
            RestaurantTable table = tableOpt.get();
            table.setStatus(TableStatus.AVAILABLE.getValue());
            tableRepository.save(table);
        }
    }
}
