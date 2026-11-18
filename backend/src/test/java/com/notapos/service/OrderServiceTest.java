package com.notapos.service;

import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.exception.OrderStateException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService.
 * 
 * Tests order management (checks/tabs for tables).
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        // Create a test order (a check for a table)
        testOrder = new Order();
        testOrder.setOrderId(1L);
        testOrder.setTableId(1L);
        testOrder.setOrderType("dine_in");
        testOrder.setStatus("open");
        testOrder.setSubtotal(BigDecimal.ZERO);
        testOrder.setTax(BigDecimal.ZERO);
        testOrder.setTotal(BigDecimal.ZERO);
    }

    // Table 1 (the table testOrder belongs to) with the given status
    private RestaurantTable table(String status) {
        RestaurantTable table = new RestaurantTable();
        table.setTableId(1L);
        table.setTableNumber("W1");
        table.setStatus(status);
        return table;
    }
    @Test
    void testCreateOrder_ShouldStartAsOpen() {
        // WHAT: Test that new orders start with "open" status
        // WHY: Orders should stay open throughout the meal
        
        // Given - The table exists and has no open order; mock returns our test order
        RestaurantTable table = table("available");
        when(tableRepository.findById(1L)).thenReturn(Optional.of(table));
        when(orderRepository.findByTableIdAndStatus(1L, "open")).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When - Create the order
        Order created = orderService.createOrder(testOrder);

        // Then - Verify it starts as "open"
        assertEquals("open", created.getStatus());
        verify(orderRepository, times(1)).save(testOrder);
    }

    @Test
    void testCreateOrder_ShouldMarkTheTableOccupied() {
        // WHAT: Test that opening an order marks its table occupied
        // WHY: The screen used to do this with a second request. If that request failed,
        //      the Floor Map showed a table with an open check as free.

        // Given
        RestaurantTable table = table("available");
        when(tableRepository.findById(1L)).thenReturn(Optional.of(table));
        when(orderRepository.findByTableIdAndStatus(1L, "open")).thenReturn(List.of());
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        orderService.createOrder(testOrder);

        // Then
        assertEquals("occupied", table.getStatus());
        verify(tableRepository, times(1)).save(table);
    }

    @Test
    void testCreateOrder_WhenTableAlreadyHasAnOpenOrder_ShouldReturnThatOrder() {
        // WHAT: Test opening an order on a table that already has an open check
        // WHY: Two servers can open the same table within seconds of each other. A second
        //      check would split the table's items, and paying one would free the table
        //      while the other was still open.

        // Given - Order 9 is already open on table 1
        Order alreadyOpen = new Order();
        alreadyOpen.setOrderId(9L);
        alreadyOpen.setTableId(1L);
        alreadyOpen.setStatus("open");
        when(tableRepository.findById(1L)).thenReturn(Optional.of(table("occupied")));
        when(orderRepository.findByTableIdAndStatus(1L, "open")).thenReturn(List.of(alreadyOpen));

        // When
        Order result = orderService.createOrder(testOrder);

        // Then - The existing order comes back and nothing new is saved
        assertEquals(9L, result.getOrderId());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testCreateOrder_WhenTableDoesNotExist_ShouldThrowException() {
        // WHAT: Test opening an order on a table that is not in the database
        // WHY: An order must belong to a real table

        // Given
        when(tableRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(testOrder));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void testGetAllOrders_ShouldReturnAllOrders() {
        // WHAT: Test retrieving all orders in the system
        // WHY: Need to see all open checks
        
        // Given - Mock returns 2 orders
        List<Order> orders = Arrays.asList(testOrder, new Order());
        when(orderRepository.findAll()).thenReturn(orders);

        // When - Get all orders
        List<Order> result = orderService.getAllOrders();

        // Then - Should get 2 orders back
        assertEquals(2, result.size());
        verify(orderRepository, times(1)).findAll();
    }

    @Test
    void testGetOrderById_WhenExists_ShouldReturnOrder() {
        // WHAT: Test finding a specific order by ID
        // WHY: Need to load a table's check
        
        // Given - Mock returns the order
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When - Look up order by ID
        Optional<Order> result = orderService.getOrderById(1L);

        // Then - Should find the order
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getOrderId());
        verify(orderRepository, times(1)).findById(1L);
    }

    @Test
    void testGetOrdersByTable_ShouldReturnTableOrders() {
        // WHAT: Test getting all orders for a specific table
        // WHY: Table might have multiple orders throughout the day
        
        // Given - Mock returns orders for table 1
        List<Order> tableOrders = Arrays.asList(testOrder);
        when(orderRepository.findByTableId(1L)).thenReturn(tableOrders);

        // When - Get orders for table 1
        List<Order> result = orderService.getOrdersByTable(1L);

        // Then - Should get that table's orders
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getTableId());
        verify(orderRepository, times(1)).findByTableId(1L);
    }

    @Test
    void testGetOpenOrdersByTable_ShouldReturnOnlyOpenOrders() {
        // WHAT: Test getting only open (active) orders for a table
        // WHY: Don't want to see completed/closed orders
        
        // Given - Mock returns open orders only
        List<Order> openOrders = Arrays.asList(testOrder);
        when(orderRepository.findByTableIdAndStatus(1L, "open")).thenReturn(openOrders);

        // When - Get open orders for table
        List<Order> result = orderService.getOpenOrdersByTable(1L);

        // Then - Should only get open orders
        assertEquals(1, result.size());
        assertEquals("open", result.get(0).getStatus());
        verify(orderRepository, times(1)).findByTableIdAndStatus(1L, "open");
    }

    @Test
    void testUpdateOrderTotals_ShouldCalculateTaxAndTotal() {
        // WHAT: Test calculating tax and total when items are added
        // WHY: Order totals need to update as items are added
        
        // Given - Order exists, tax rate is 8%
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        BigDecimal subtotal = new BigDecimal("50.00");
        BigDecimal taxRate = new BigDecimal("0.08");

        // When - Update totals with $50 subtotal and 8% tax
        Order updated = orderService.updateOrderTotals(1L, subtotal, taxRate);

        // Then - Should calculate $4.00 tax and $54.00 total
        assertEquals(new BigDecimal("50.00"), updated.getSubtotal());
        assertEquals(new BigDecimal("4.00"), updated.getTax());
        assertEquals(new BigDecimal("54.00"), updated.getTotal());
        verify(orderRepository, times(1)).save(testOrder);
    }

    @Test
    void testUpdateOrderTotals_WhenOrderNotFound_ShouldThrowException() {
        // WHAT: Test error handling when order doesn't exist
        // WHY: Can't update totals for non-existent order
        
        // Given - Order doesn't exist
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then - Should throw exception
        assertThrows(RuntimeException.class, () -> {
            orderService.updateOrderTotals(999L, BigDecimal.TEN, new BigDecimal("0.08"));
        });
    }

    @Test
    void testCloseOrder_ShouldSetStatusAndTimestamp() {
        // WHAT: Test closing an order (guest pays and leaves)
        // WHY: Need to close the check when guest is done
        
        // Given - Order exists and is open
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - Close the order
        Order closed = orderService.closeOrder(1L);

        // Then - Status should be "closed" with timestamp
        assertEquals("closed", closed.getStatus());
        assertNotNull(closed.getClosedAt());
        verify(orderRepository, times(1)).save(testOrder);
    }

    @Test
    void testGetOrdersByStatus_ShouldFilterByStatus() {
        // WHAT: Test filtering orders by status
        // WHY: Want to see all open orders, or all completed orders
        
        // Given - Mock returns open orders
        List<Order> openOrders = Arrays.asList(testOrder);
        when(orderRepository.findByStatus("open")).thenReturn(openOrders);

        // When - Get all open orders
        List<Order> result = orderService.getOrdersByStatus("open");

        // Then - Should get only open orders
        assertEquals(1, result.size());
        assertEquals("open", result.get(0).getStatus());
        verify(orderRepository, times(1)).findByStatus("open");
    }
    @Test
    void testDeleteOrder_ShouldCallRepository() {
        // WHAT: Test deleting an order
        // WHY: Need to remove test/cancelled orders
        
        // Given - An open order with no items on it
        RestaurantTable table = table("occupied");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());
        when(tableRepository.findById(1L)).thenReturn(Optional.of(table));
        when(orderRepository.findByTableIdAndStatus(1L, "open")).thenReturn(List.of());

        // When - Delete order
        orderService.deleteOrder(1L);

        // Then - Repository delete should be called, and the table is free again
        verify(orderRepository, times(1)).deleteById(1L);
        assertEquals("available", table.getStatus());
        verify(tableRepository, times(1)).save(table);
    }

    @Test
    void testDeleteOrder_WhenOrderHasItems_ShouldThrowAndDeleteNothing() {
        // WHAT: Test clearing an order that still has items on it
        // WHY: Deleting it would leave its items pointing at nothing, and food that was
        //      rung in (maybe already cooked) would vanish from the check

        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(new OrderItem()));

        // When & Then
        assertThrows(OrderStateException.class, () -> orderService.deleteOrder(1L));
        verify(orderRepository, never()).deleteById(any());
        verify(tableRepository, never()).save(any());
    }

    @Test
    void testDeleteOrder_WhenOrderIsClosed_ShouldThrowAndDeleteNothing() {
        // WHAT: Test deleting a paid order
        // WHY: Closed orders are the sales history

        // Given
        testOrder.setStatus("closed");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When & Then
        assertThrows(OrderStateException.class, () -> orderService.deleteOrder(1L));
        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void testDeleteOrder_WhenNotFound_ShouldThrowException() {
        // WHAT: Test deleting an order that does not exist
        // WHY: The screen should hear about it (404) instead of thinking the table was cleared

        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> orderService.deleteOrder(1L));
    }
}