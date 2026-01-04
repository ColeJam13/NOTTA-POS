package com.notapos.controller;

import com.notapos.entity.Order;
import com.notapos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for OrderController.
 * 
 * Tests REST API endpoints for order management (checks/tabs).
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        // Create test order
        testOrder = new Order();
        testOrder.setOrderId(1L);
        testOrder.setTableId(1L);
        testOrder.setOrderType("dine_in");
        testOrder.setStatus("open");
        testOrder.setSubtotal(new BigDecimal("50.00"));
        testOrder.setTax(new BigDecimal("4.00"));
        testOrder.setTotal(new BigDecimal("54.00"));
    }

    @Test
    void testGetAllOrders_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/orders endpoint
        // WHY: Retrieve all orders in system
        
        // Given - Service returns list of orders
        List<Order> orders = Arrays.asList(testOrder);
        when(orderService.getAllOrders()).thenReturn(orders);

        // When/Then - GET request should return 200 OK with orders
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].status").value("open"));

        verify(orderService, times(1)).getAllOrders();
    }

    @Test
    void testGetAllOrders_WithStatusFilter_ShouldReturnFilteredList() throws Exception {
        // WHAT: Test GET /api/orders?status=open
        // WHY: Filter orders by status
        
        // Given - Service returns open orders
        List<Order> openOrders = Arrays.asList(testOrder);
        when(orderService.getOrdersByStatus("open")).thenReturn(openOrders);

        // When/Then - GET with status param should return filtered orders
        mockMvc.perform(get("/api/orders").param("status", "open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("open"));

        verify(orderService, times(1)).getOrdersByStatus("open");
    }

    @Test
    void testGetOrderById_WhenExists_ShouldReturnOrder() throws Exception {
        // WHAT: Test GET /api/orders/{id} when order exists
        // WHY: Retrieve specific order details
        
        // Given - Service returns the order
        when(orderService.getOrderById(1L)).thenReturn(Optional.of(testOrder));

        // When/Then - GET request should return 200 OK with order
        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("open"));

        verify(orderService, times(1)).getOrderById(1L);
    }

    @Test
    void testGetOrderById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/orders/{id} when order doesn't exist
        // WHY: Handle missing orders gracefully
        
        // Given - Service returns empty
        when(orderService.getOrderById(999L)).thenReturn(Optional.empty());

        // When/Then - GET request should return 404 Not Found
        mockMvc.perform(get("/api/orders/999"))
                .andExpect(status().isNotFound());

        verify(orderService, times(1)).getOrderById(999L);
    }

    @Test
    void testGetOrdersByTable_ShouldReturnTableOrders() throws Exception {
        // WHAT: Test GET /api/orders/table/{tableId}
        // WHY: Get all orders for specific table
        
        // Given - Service returns orders for table
        List<Order> tableOrders = Arrays.asList(testOrder);
        when(orderService.getOrdersByTable(1L)).thenReturn(tableOrders);

        // When/Then - GET request should return 200 OK with orders
        mockMvc.perform(get("/api/orders/table/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tableId").value(1));

        verify(orderService, times(1)).getOrdersByTable(1L);
    }

    @Test
    void testGetOrdersByTable_WithOpenStatus_ShouldReturnOpenOrders() throws Exception {
        // WHAT: Test GET /api/orders/table/{tableId}?status=open
        // WHY: Get only open orders for table
        
        // Given - Service returns open orders for table
        List<Order> openTableOrders = Arrays.asList(testOrder);
        when(orderService.getOpenOrdersByTable(1L)).thenReturn(openTableOrders);

        // When/Then - GET with status=open should return open orders
        mockMvc.perform(get("/api/orders/table/1").param("status", "open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("open"));

        verify(orderService, times(1)).getOpenOrdersByTable(1L);
    }

    @Test
    void testCreateOrder_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/orders to create new order
        // WHY: Start new check for table
        
        // Given - Service returns created order
        when(orderService.createOrder(any(Order.class))).thenReturn(testOrder);

        // When/Then - POST request should return 201 Created
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":1,\"orderType\":\"dine_in\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("open"));

        verify(orderService, times(1)).createOrder(any(Order.class));
    }

    @Test
    void testUpdateOrderTotals_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/orders/{id}/totals to update order totals
        // WHY: Recalculate totals when items are added/removed
        
        // Given - Service returns updated order
        testOrder.setSubtotal(new BigDecimal("60.00"));
        testOrder.setTax(new BigDecimal("4.80"));
        testOrder.setTotal(new BigDecimal("64.80"));
        when(orderService.updateOrderTotals(eq(1L), any(BigDecimal.class), any(BigDecimal.class)))
                .thenReturn(testOrder);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/orders/1/totals")
                .param("subtotal", "60.00")
                .param("taxRate", "0.08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(60.00))
                .andExpect(jsonPath("$.total").value(64.80));

        verify(orderService, times(1)).updateOrderTotals(eq(1L), any(BigDecimal.class), any(BigDecimal.class));
    }

    @Test
    void testCompleteOrder_ShouldReturnCompleted() throws Exception {
        // WHAT: Test PUT /api/orders/{id}complete to close check
        // WHY: Mark order as completed when guest pays
        
        // Given - Service returns completed order
        testOrder.setStatus("completed");
        when(orderService.completeOrder(1L)).thenReturn(testOrder);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/orders/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        verify(orderService, times(1)).completeOrder(1L);
    }

    @Test
    void testDeleteOrder_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/orders/{id}
        // WHY: Remove cancelled or test orders
        
        // Given - Service delete method exists
        doNothing().when(orderService).deleteOrder(1L);

        // When/Then - DELETE request should return 204 No Content
        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isNoContent());

        verify(orderService, times(1)).deleteOrder(1L);
    }
}