package com.notapos.repository;

import com.notapos.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for OrderRepository.
 * 
 * Tests database queries for order management.
 * 
 * @author CJ
 */

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    private Order openOrder;
    private Order completedOrder;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        orderRepository.deleteAll();

        // Create open order for table 1
        openOrder = new Order();
        openOrder.setTableId(1L);
        openOrder.setOrderType("dine_in");
        openOrder.setStatus("open");
        openOrder.setSubtotal(new BigDecimal("25.00"));
        openOrder.setTax(new BigDecimal("2.00"));
        openOrder.setTotal(new BigDecimal("27.00"));
        openOrder = orderRepository.save(openOrder);

        // Create completed order for table 2
        completedOrder = new Order();
        completedOrder.setTableId(2L);
        completedOrder.setOrderType("dine_in");
        completedOrder.setStatus("completed");
        completedOrder.setSubtotal(new BigDecimal("45.00"));
        completedOrder.setTax(new BigDecimal("3.60"));
        completedOrder.setTotal(new BigDecimal("48.60"));
        completedOrder = orderRepository.save(completedOrder);
    }

    @Test
    void testSave_ShouldPersistOrder() {
        // WHAT: Test saving a new order to database
        // WHY: Ensure basic create operation works
        
        // Given - New order
        Order newOrder = new Order();
        newOrder.setTableId(3L);
        newOrder.setOrderType("takeout");
        newOrder.setStatus("open");
        newOrder.setSubtotal(new BigDecimal("15.00"));
        newOrder.setTax(new BigDecimal("1.20"));
        newOrder.setTotal(new BigDecimal("16.20"));

        // When - Save to database
        Order saved = orderRepository.save(newOrder);

        // Then - Should persist with generated ID
        assertNotNull(saved.getOrderId());
        assertEquals(3L, saved.getTableId());
        assertEquals("open", saved.getStatus());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnOrder() {
        // WHAT: Test finding order by ID
        // WHY: Need to load specific orders for updates/viewing
        
        // Given - Order exists in database (from setUp)
        
        // When - Find by ID
        Optional<Order> result = orderRepository.findById(openOrder.getOrderId());

        // Then - Should find the order
        assertTrue(result.isPresent());
        assertEquals("open", result.get().getStatus());
        assertEquals(1L, result.get().getTableId());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent order
        // WHY: Handle missing orders gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<Order> result = orderRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllOrders() {
        // WHAT: Test retrieving all orders
        // WHY: Get complete view of all active checks
        
        // Given - 2 orders in database (from setUp)
        
        // When - Find all
        List<Order> orders = orderRepository.findAll();

        // Then - Should get all 2 orders
        assertEquals(2, orders.size());
    }

    @Test
    void testFindByTableId_ShouldReturnOrdersForTable() {
        // WHAT: Test finding all orders for a specific table
        // WHY: Show server all checks for their table
        
        // Given - Table 1 has 1 order, Table 2 has 1 order (from setUp)
        
        // When - Find orders for Table 1
        List<Order> table1Orders = orderRepository.findByTableId(1L);

        // Then - Should get 1 order for Table 1
        assertEquals(1, table1Orders.size());
        assertEquals(1L, table1Orders.get(0).getTableId());
    }

    @Test
    void testFindByStatus_Open_ShouldReturnOpenOrders() {
        // WHAT: Test finding orders with open status
        // WHY: Show all active checks in restaurant
        
        // Given - 1 open order exists (from setUp)
        
        // When - Find open orders
        List<Order> openOrders = orderRepository.findByStatus("open");

        // Then - Should get only open orders
        assertEquals(1, openOrders.size());
        assertEquals("open", openOrders.get(0).getStatus());
    }

    @Test
    void testFindByStatus_Completed_ShouldReturnCompletedOrders() {
        // WHAT: Test finding orders with completed status
        // WHY: Show closed checks for end-of-shift reports
        
        // Given - 1 completed order exists (from setUp)
        
        // When - Find completed orders
        List<Order> completed = orderRepository.findByStatus("completed");

        // Then - Should get only completed orders
        assertEquals(1, completed.size());
        assertEquals("completed", completed.get(0).getStatus());
    }

    @Test
    void testFindByTableIdAndStatus_ShouldReturnMatchingOrders() {
        // WHAT: Test finding orders by both table ID and status
        // WHY: Check if specific table has open checks
        
        // Given - Table 1 has 1 open order (from setUp)
        
        // When - Find open orders for Table 1
        List<Order> table1Open = orderRepository.findByTableIdAndStatus(1L, "open");

        // Then - Should get the matching order
        assertEquals(1, table1Open.size());
        assertEquals(1L, table1Open.get(0).getTableId());
        assertEquals("open", table1Open.get(0).getStatus());
    }

    @Test
    void testFindOrdersBetween_ShouldReturnOrdersInTimeRange() {
        // WHAT: Test finding orders created within a time range
        // WHY: Generate shift reports and sales analytics
        
        // Given - Orders created at different times
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        
        // When - Find orders in time range
        List<Order> ordersInRange = orderRepository.findOrdersBetween(start, end);

        // Then - Should find both orders (they were just created)
        assertEquals(2, ordersInRange.size());
    }

    @Test
    void testDeleteById_ShouldRemoveOrder() {
        // WHAT: Test deleting an order
        // WHY: Remove test data or cancelled orders
        
        // Given - Open order exists
        Long orderId = openOrder.getOrderId();
        
        // When - Delete the order
        orderRepository.deleteById(orderId);

        // Then - Order should no longer exist
        Optional<Order> deleted = orderRepository.findById(orderId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingOrder() {
        // WHAT: Test updating an order's fields
        // WHY: Update totals when items are added/removed
        
        // Given - Open order exists
        Long orderId = openOrder.getOrderId();
        
        // When - Update order totals
        openOrder.setSubtotal(new BigDecimal("30.00"));
        openOrder.setTax(new BigDecimal("2.40"));
        openOrder.setTotal(new BigDecimal("32.40"));
        Order updated = orderRepository.save(openOrder);

        // Then - Changes should persist
        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertEquals(new BigDecimal("30.00"), reloaded.getSubtotal());
        assertEquals(new BigDecimal("32.40"), reloaded.getTotal());
    }
}