package com.notapos.controller;

import com.notapos.entity.OrderItem;
import com.notapos.service.OrderItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for OrderItemController.
 * 
 * Tests REST API endpoints for order item management (delay timer feature).
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@WebMvcTest(OrderItemController.class)
class OrderItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderItemService orderItemService;

    private OrderItem testItem;

    @BeforeEach
    void setUp() {
        // Create test order item
        testItem = new OrderItem();
        testItem.setOrderItemId(1L);
        testItem.setOrderId(1L);
        testItem.setMenuItemId(1L);
        testItem.setQuantity(1);
        testItem.setPrice(new BigDecimal("17.00"));
        testItem.setStatus("draft");
        testItem.setDelaySeconds(15);
        testItem.setIsLocked(false);
    }

    @Test
    void testGetAllOrderItems_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/order-items endpoint
        // WHY: Retrieve all order items in system
        
        // Given - Service returns list of items
        List<OrderItem> items = Arrays.asList(testItem);
        when(orderItemService.getAllOrderItems()).thenReturn(items);

        // When/Then - GET request should return 200 OK with items
        mockMvc.perform(get("/api/order-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderItemId").value(1))
                .andExpect(jsonPath("$[0].status").value("draft"));

        verify(orderItemService, times(1)).getAllOrderItems();
    }

    @Test
    void testGetAllOrderItems_WithStatusFilter_ShouldReturnFilteredList() throws Exception {
        // WHAT: Test GET /api/order-items?status=draft
        // WHY: Filter items by status
        
        // Given - Service returns draft items
        List<OrderItem> draftItems = Arrays.asList(testItem);
        when(orderItemService.getItemsByStatus("draft")).thenReturn(draftItems);

        // When/Then - GET with status param should return filtered items
        mockMvc.perform(get("/api/order-items").param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("draft"));

        verify(orderItemService, times(1)).getItemsByStatus("draft");
    }

    @Test
    void testGetOrderItemById_WhenExists_ShouldReturnItem() throws Exception {
        // WHAT: Test GET /api/order-items/{id} when item exists
        // WHY: Retrieve specific order item details
        
        // Given - Service returns the item
        when(orderItemService.getOrderItemById(1L)).thenReturn(Optional.of(testItem));

        // When/Then - GET request should return 200 OK with item
        mockMvc.perform(get("/api/order-items/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(1))
                .andExpect(jsonPath("$.status").value("draft"));

        verify(orderItemService, times(1)).getOrderItemById(1L);
    }

    @Test
    void testGetOrderItemById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/order-items/{id} when item doesn't exist
        // WHY: Handle missing items gracefully
        
        // Given - Service returns empty
        when(orderItemService.getOrderItemById(999L)).thenReturn(Optional.empty());

        // When/Then - GET request should return 404 Not Found
        mockMvc.perform(get("/api/order-items/999"))
                .andExpect(status().isNotFound());

        verify(orderItemService, times(1)).getOrderItemById(999L);
    }

    @Test
    void testCreateOrderItem_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/order-items to create new item
        // WHY: Add items to order
        
        // Given - Service returns created item
        when(orderItemService.createOrderItem(any(OrderItem.class))).thenReturn(testItem);

        // When/Then - POST request should return 201 Created
        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"menuItemId\":1,\"quantity\":1,\"price\":17.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderItemId").value(1))
                .andExpect(jsonPath("$.status").value("draft"));

        verify(orderItemService, times(1)).createOrderItem(any(OrderItem.class));
    }

    @Test
    void testUpdateOrderItem_WhenExists_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/order-items/{id} to update item
        // WHY: Edit item details during delay window
        
        // Given - Service returns updated item
        testItem.setQuantity(2);
        when(orderItemService.updateOrderItem(eq(1L), any(OrderItem.class))).thenReturn(testItem);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/order-items/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"menuItemId\":1,\"quantity\":2,\"price\":17.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(2));

        verify(orderItemService, times(1)).updateOrderItem(eq(1L), any(OrderItem.class));
    }

    @Test
    void testDeleteOrderItem_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/order-items/{id}
        // WHY: Remove mistakenly added items
        
        // Given - Service delete method exists
        doNothing().when(orderItemService).deleteOrderItem(1L);

        // When/Then - DELETE request should return 204 No Content
        mockMvc.perform(delete("/api/order-items/1"))
                .andExpect(status().isNoContent());

        verify(orderItemService, times(1)).deleteOrderItem(1L);
    }

    @Test
    void testGetItemsByOrder_ShouldReturnOrderItems() throws Exception {
        // WHAT: Test GET /api/order-items/order/{orderId}
        // WHY: Get all items for specific order
        
        // Given - Service returns items for order
        List<OrderItem> orderItems = Arrays.asList(testItem);
        when(orderItemService.getItemsByOrder(1L)).thenReturn(orderItems);

        // When/Then - GET request should return 200 OK with items
        mockMvc.perform(get("/api/order-items/order/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1));

        verify(orderItemService, times(1)).getItemsByOrder(1L);
    }

    @Test
    void testGetItemsByOrder_WithDraftStatus_ShouldReturnDraftItems() throws Exception {
        // WHAT: Test GET /api/order-items/order/{orderId}?status=draft
        // WHY: Get draft items for order when server clicks "Send"
        
        // Given - Service returns draft items
        List<OrderItem> draftItems = Arrays.asList(testItem);
        when(orderItemService.getDraftItemsByOrder(1L)).thenReturn(draftItems);

        // When/Then - GET with status=draft should return draft items
        mockMvc.perform(get("/api/order-items/order/1").param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("draft"));

        verify(orderItemService, times(1)).getDraftItemsByOrder(1L);
    }

    @Test
    void testGetItemsByOrder_WithPendingStatus_ShouldReturnPendingItems() throws Exception {
        // WHAT: Test GET /api/order-items/order/{orderId}?status=pending
        // WHY: Show items in delay timer window
        
        // Given - Service returns pending items
        testItem.setStatus("pending");
        List<OrderItem> pendingItems = Arrays.asList(testItem);
        when(orderItemService.getPendingItemsByOrder(1L)).thenReturn(pendingItems);

        // When/Then - GET with status=pending should return pending items
        mockMvc.perform(get("/api/order-items/order/1").param("status", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("pending"));

        verify(orderItemService, times(1)).getPendingItemsByOrder(1L);
    }

    @Test
    void testSendItemsForOrder_ShouldStartTimer() throws Exception {
        // WHAT: Test POST /api/order-items/order/{orderId}/send
        // WHY: Start delay timer when server clicks "Send"
        
        // Given - Service returns sent items with timer started
        testItem.setStatus("pending");
        testItem.setDelayExpiresAt(LocalDateTime.now().plusSeconds(15));
        List<OrderItem> sentItems = Arrays.asList(testItem);
        when(orderItemService.sendItemsForOrder(1L)).thenReturn(sentItems);

        // When/Then - POST request should return 200 OK
        mockMvc.perform(post("/api/order-items/order/1/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("pending"));

        verify(orderItemService, times(1)).sendItemsForOrder(1L);
    }

    @Test
    void testSendItemNow_ShouldBypassTimer() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/send-now
        // WHY: Manager override to send immediately
        
        // Given - Service sends item immediately
        testItem.setStatus("pending");
        testItem.setIsLocked(true);
        when(orderItemService.sendItemNow(1L)).thenReturn(testItem);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/order-items/1/send-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.isLocked").value(true));

        verify(orderItemService, times(1)).sendItemNow(1L);
    }

    @Test
    void testCompleteItem_ShouldMarkCompleted() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/complete
        // WHY: Mark item as completed when food is ready
        
        // Given - Service completes item
        testItem.setStatus("completed");
        when(orderItemService.completeItem(1L)).thenReturn(testItem);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/order-items/1/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        verify(orderItemService, times(1)).completeItem(1L);
    }
}