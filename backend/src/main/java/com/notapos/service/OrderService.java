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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
     *
     * The table row is locked for the length of this method, so two terminals
     * opening the same table at the same instant go one after the other: the
     * second waits, then finds the order the first one opened. Without the lock
     * both would look, both would find nothing, and both would open a check.
     * Behind that, the database has its own rule (the index
     * orders_one_open_per_table, added in migration V3) and refuses a second
     * open order on a table whatever the code does.
     *
     * Only the table, order type, and server are taken from the request. A new
     * Order is built here and the rest starts fresh. Saving the request's own
     * object would be wrong: an object that carries an ID means "update that
     * row" to the database layer, so a request naming an existing order's ID
     * used to overwrite it - a paid check could be turned back into an open one.
     */
    @Transactional
    public Order createOrder(Order request) {                 // create new order
        RestaurantTable table = tableRepository.findByIdLockedForUpdate(request.getTableId())
                .orElseThrow(() -> new ResourceNotFoundException("Table not found with id: " + request.getTableId()));

        List<Order> alreadyOpen = orderRepository.findByTableIdAndStatus(request.getTableId(), OrderStatus.OPEN.getValue());
        if (!alreadyOpen.isEmpty()) {
            return alreadyOpen.get(0);
        }

        Order order = new Order();                              // No ID: this is always a new row
        order.setTableId(request.getTableId());
        if (request.getOrderType() != null) {
            order.setOrderType(request.getOrderType());         // Otherwise the entity's default (dine_in) stands
        }
        order.setServerName(request.getServerName());
        order.setStaffId(request.getStaffId());
        order.setStatus(OrderStatus.OPEN.getValue());
        Order saved = orderRepository.save(order);

        table.setStatus(TableStatus.OCCUPIED.getValue());
        tableRepository.save(table);

        return saved;
    }

    // There is no "close order" or "update totals" here. An order is closed by a payment
    // (PaymentService.createPayment, which also records its totals) or cleared when empty
    // (deleteOrder below). The two methods that did it directly were removed in step A3b:
    // one closed a check with no payment, the other took the tax rate from the caller.

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
     *
     * The order is held alone while this runs ("for update", see OrderRepository),
     * so another terminal cannot ring an item onto it between the "is it empty?"
     * check and the delete. That item would have been left pointing at an order
     * that no longer exists.
     */
    @Transactional
    public void deleteOrder(Long id) {                          // delete an order
        Order order = orderRepository.findByIdLockedForUpdate(id)
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
