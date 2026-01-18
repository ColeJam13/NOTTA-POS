package com.notapos.service;

import com.notapos.entity.Order;
import com.notapos.repository.OrderRepository;
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

    @Test
    void testCreateOrder_ShouldStartAsOpen() {
        // WHAT: Test that new orders start with "open" status
        // WHY: Orders should stay open throughout the meal
        
        // Given - Set up mock to return our test order
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When - Create the order
        Order created = orderService.createOrder(testOrder);

        // Then - Verify it starts as "open"
        assertEquals("open", created.getStatus());
        verify(orderRepository, times(1)).save(testOrder);
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
        
        // Given - Mock repository
        doNothing().when(orderRepository).deleteById(1L);

        // When - Delete order
        orderService.deleteOrder(1L);

        // Then - Repository delete should be called
        verify(orderRepository, times(1)).deleteById(1L);
    }
}