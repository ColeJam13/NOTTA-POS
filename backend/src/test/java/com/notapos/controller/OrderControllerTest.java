package com.notapos.controller;

import com.notapos.enums.StaffRole;
import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.exception.OrderStateException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.service.OrderCheckService;
import com.notapos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
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

@ControllerTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The real sign-in check runs in controller tests; signIn makes a request come from a signed-in person (see SignIn)
    @Autowired
    private SignIn signIn;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderCheckService orderCheckService;

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
    void testCloseOrder_ShouldReturnClosed() throws Exception {
        // WHAT: Test PUT /api/orders/{id}/close to close check
        // WHY: Mark order as closed when guest pays
        
        // Given - Service returns closed order
        testOrder.setStatus("closed");
        when(orderService.closeOrder(1L)).thenReturn(testOrder);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/orders/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));

        verify(orderService, times(1)).closeOrder(1L);
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

    @Test
    void testDeleteOrder_WhenOrderHasItems_ShouldReturn409() throws Exception {
        // WHAT: Test DELETE /api/orders/{id} on an order that still has items
        // WHY: Only an empty order can be cleared off its table; the screen shows the message

        doThrow(new OrderStateException("Order #1 still has items on it. Remove them or close the check."))
                .when(orderService).deleteOrder(1L);

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order #1 still has items on it. Remove them or close the check."));
    }

    @Test
    void testGetOrderCheck_ShouldReturnTheCheck() throws Exception {
        // WHAT: Test GET /api/orders/{id}/check
        // WHY: The Payment screen shows the backend's numbers, not its own arithmetic

        when(orderCheckService.getCheck(1L)).thenReturn(new OrderCheckView(
                1L, "open", 2, 1,
                new BigDecimal("33.00"), new BigDecimal("0.99"), new BigDecimal("33.99"), new BigDecimal("0.03")));

        mockMvc.perform(get("/api/orders/1/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.unsentItemCount").value(1))
                .andExpect(jsonPath("$.subtotal").value(33.00))
                .andExpect(jsonPath("$.tax").value(0.99))
                .andExpect(jsonPath("$.total").value(33.99))
                .andExpect(jsonPath("$.taxRate").value(0.03));
    }

    @Test
    void testGetOrderCheck_WhenOrderNotFound_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/orders/{id}/check for an order that does not exist
        // WHY: The screen needs a clear answer, not an empty check

        when(orderCheckService.getCheck(99L)).thenThrow(new ResourceNotFoundException("Order not found with id: 99"));

        mockMvc.perform(get("/api/orders/99/check"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCreateOrder_WhenSomeoneIsSignedIn_ShouldPutTheirNameOnTheOrder() throws Exception {
        // WHAT: Test POST /api/orders with a signed-in server
        // WHY: An order belongs to whoever rang it in. The name the screen sends is ignored
        //      in favor of the person the backend knows is signed in.

        when(orderService.createOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/orders").with(signIn.as(7L, "Sarah", StaffRole.SERVER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":1,\"orderType\":\"dine_in\",\"serverName\":\"Somebody Else\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serverName").value("Sarah"))
                .andExpect(jsonPath("$.staffId").value(7));
    }

    @Test
    void testCreateOrder_WhenNobodyIsSignedIn_ShouldKeepTheNameThatWasSent() throws Exception {
        // WHAT: Test POST /api/orders with no sign-in
        // WHY: Opening an order is not restricted, so older screens keep working

        when(orderService.createOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":1,\"orderType\":\"dine_in\",\"serverName\":\"CJ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serverName").value("CJ"))
                .andExpect(jsonPath("$.staffId").value(org.hamcrest.Matchers.nullValue()));
    }
}
