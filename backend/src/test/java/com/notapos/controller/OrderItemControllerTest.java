package com.notapos.controller;

import com.notapos.dto.OrderItemModifiersRequest;
import com.notapos.dto.OrderItemRequest;
import com.notapos.exception.InvalidModifierSelectionException;
import com.notapos.exception.ItemLockedException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.entity.OrderItem;
import com.notapos.service.OrderItemModifierService;
import com.notapos.service.OrderItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
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

@ControllerTest(OrderItemController.class)
class OrderItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderItemService orderItemService;

    @MockBean
    private OrderItemModifierService orderItemModifierService;

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

    @Test
    void testSendOrderNow_ShouldSendAllDraftAndLimboItems() throws Exception {
        // WHAT: Test POST /api/order-items/order/{orderId}/send-now
        // WHY: Send all draft and limbo items immediately (manager override)
        
        // Given - Service locks and returns both items in one call
        OrderItem sentDraft = new OrderItem();
        sentDraft.setOrderItemId(1L);
        sentDraft.setStatus("pending");
        sentDraft.setIsLocked(true);
        
        OrderItem sentLimbo = new OrderItem();
        sentLimbo.setOrderItemId(2L);
        sentLimbo.setStatus("pending");
        sentLimbo.setIsLocked(true);
        
        when(orderItemService.sendOrderNow(1L)).thenReturn(Arrays.asList(sentDraft, sentLimbo));

        // When/Then - POST request should return 200 OK with sent items
        mockMvc.perform(post("/api/order-items/order/1/send-now"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("pending"))
                .andExpect(jsonPath("$[1].isLocked").value(true));

        verify(orderItemService, times(1)).sendOrderNow(1L);
    }

    @Test
    void testStartItem_WhenSuccessful_ShouldReturnStartedItem() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/start
        // WHY: Kitchen marks item as started
        
        // Given - Service starts the item
        testItem.setStatus("fired");
        when(orderItemService.startItem(1L)).thenReturn(testItem);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/order-items/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(1))
                .andExpect(jsonPath("$.status").value("fired"));

        verify(orderItemService, times(1)).startItem(1L);
    }

    @Test
    void testStartItem_WhenItemNotFound_ShouldReturn404() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/start when item doesn't exist
        // WHY: Handle missing items gracefully
        
        // Given - Service throws exception
        when(orderItemService.startItem(999L)).thenThrow(new ResourceNotFoundException("Item not found"));

        // When/Then - PUT request should return 404 Not Found
        mockMvc.perform(put("/api/order-items/999/start"))
                .andExpect(status().isNotFound());

        verify(orderItemService, times(1)).startItem(999L);
    }

    @Test
    void testLockExpiredItems_ShouldReturnLockedItems() throws Exception {
        // WHAT: Test POST /api/order-items/lock-expired
        // WHY: Background job locks items when delay timer expires
        
        // Given - Service returns locked items
        testItem.setStatus("pending");
        testItem.setIsLocked(true);
        List<OrderItem> lockedItems = Arrays.asList(testItem);
        when(orderItemService.lockAndSendExpiredItems()).thenReturn(lockedItems);

        // When/Then - POST request should return 200 OK with locked items
        mockMvc.perform(post("/api/order-items/lock-expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isLocked").value(true));

        verify(orderItemService, times(1)).lockAndSendExpiredItems();
    }

    // ========== ERROR SCENARIO TESTS ==========

    @Test
    void testSendItemNow_WhenServiceThrowsException_ShouldReturn404() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/send-now when item doesn't exist
        // WHY: Handle missing items
        
        // Given - Service throws exception
        when(orderItemService.sendItemNow(999L)).thenThrow(new ResourceNotFoundException("Item not found"));

        // When/Then - PUT request should return 404 Not Found
        mockMvc.perform(put("/api/order-items/999/send-now"))
                .andExpect(status().isNotFound());

        verify(orderItemService, times(1)).sendItemNow(999L);
    }

    @Test
    void testCompleteItem_WhenServiceThrowsException_ShouldReturn404() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/complete when item doesn't exist
        // WHY: Handle missing items
        
        // Given - Service throws exception
        when(orderItemService.completeItem(999L)).thenThrow(new ResourceNotFoundException("Item not found"));

        // When/Then - PUT request should return 404 Not Found
        mockMvc.perform(put("/api/order-items/999/complete"))
                .andExpect(status().isNotFound());

        verify(orderItemService, times(1)).completeItem(999L);
    }

    @Test
    void testDeleteOrderItem_WhenItemLocked_ShouldReturnConflict() throws Exception {
        // WHAT: Test DELETE /api/order-items/{id} when delete fails
        // WHY: Handle locked items that can't be deleted
        
        // Given - Service throws exception
        doThrow(new ItemLockedException("Item is locked")).when(orderItemService).deleteOrderItem(1L);

        // When/Then - DELETE request should return 409 Conflict (item exists but is locked)
        mockMvc.perform(delete("/api/order-items/1"))
                .andExpect(status().isConflict());

        verify(orderItemService, times(1)).deleteOrderItem(1L);
    }

    @Test
    void testGetItemsByOrder_WithUnsupportedStatus_ShouldReturnAllItems() throws Exception {
        // WHAT: Test GET /api/order-items/order/{orderId}?status=fired (not draft/pending)
        // WHY: Fallback to all items when status is not draft or pending
        
        // Given - Service returns all items
        List<OrderItem> allItems = Arrays.asList(testItem);
        when(orderItemService.getItemsByOrder(1L)).thenReturn(allItems);

        // When/Then - GET with unsupported status should return all items
        mockMvc.perform(get("/api/order-items/order/1").param("status", "fired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(1));

        verify(orderItemService, times(1)).getItemsByOrder(1L);
        verify(orderItemService, never()).getDraftItemsByOrder(any());
        verify(orderItemService, never()).getPendingItemsByOrder(any());
    }

    // ------------------------------------------------------------------------
    // Items with modifiers and kitchen notes
    // ------------------------------------------------------------------------

    @Test
    void testRingInItem_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/order-items
        // WHY: Ring in an item together with its modifiers and kitchen note

        // Given - Service returns the saved item, priced with its upcharge
        testItem.setPrice(new BigDecimal("19.00"));
        testItem.setSpecialInstructions("no onions");
        when(orderItemModifierService.ringInItem(any(OrderItemRequest.class))).thenReturn(testItem);

        // When/Then - POST request should return 201 Created (note: the request carries no price)
        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"menuItemId\":1,\"quantity\":1,\"specialInstructions\":\"no onions\",\"modifierIds\":[14,16]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderItemId").value(1))
                .andExpect(jsonPath("$.price").value(19.00))
                .andExpect(jsonPath("$.specialInstructions").value("no onions"))
                .andExpect(jsonPath("$.status").value("draft"));

        // The controller handed the service exactly what was sent
        verify(orderItemModifierService, times(1)).ringInItem(
                new OrderItemRequest(1L, 1L, 1, "no onions", List.of(14L, 16L)));
    }

    @Test
    void testRingInItem_WhenOrderIdMissing_ShouldReturn400() throws Exception {
        // WHAT: Test POST /api/order-items without an order
        // WHY: @Valid rejects the request before it reaches the service

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":1,\"modifierIds\":[14]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.orderId").exists());

        verify(orderItemModifierService, never()).ringInItem(any());
    }

    @Test
    void testRingInItem_WhenNoteTooLong_ShouldReturn400() throws Exception {
        // WHAT: Test a kitchen note longer than the 500 characters the database column holds
        // WHY: Reject it with a clear message instead of a database error

        String longNote = "x".repeat(501);

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"menuItemId\":1,\"specialInstructions\":\"" + longNote + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.specialInstructions").exists());

        verify(orderItemModifierService, never()).ringInItem(any());
    }

    @Test
    void testRingInItem_WhenSelectionInvalid_ShouldReturn400() throws Exception {
        // WHAT: Test POST with modifiers that break the menu's rules
        // WHY: A required group left unanswered is the caller's mistake (400), not a server error (500)

        // Given - Service rejects the selection
        when(orderItemModifierService.ringInItem(any(OrderItemRequest.class)))
                .thenThrow(new InvalidModifierSelectionException("\"Bread Choice\" must be chosen for Combo Plate"));

        // When/Then
        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"menuItemId\":1,\"modifierIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("\"Bread Choice\" must be chosen for Combo Plate"));
    }

    @Test
    void testReplaceModifiers_WhenExists_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/modifiers
        // WHY: Change an item's modifiers and note before it locks

        // Given - Service returns the updated item
        testItem.setPrice(new BigDecimal("19.00"));
        testItem.setSpecialInstructions("nut allergy");
        when(orderItemModifierService.replaceModifiersForOrderItem(eq(1L), any(OrderItemModifiersRequest.class)))
                .thenReturn(testItem);

        // When/Then - PUT request should return 200 OK
        mockMvc.perform(put("/api/order-items/1/modifiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialInstructions\":\"nut allergy\",\"modifierIds\":[14]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(19.00))
                .andExpect(jsonPath("$.specialInstructions").value("nut allergy"));

        verify(orderItemModifierService, times(1)).replaceModifiersForOrderItem(
                1L, new OrderItemModifiersRequest("nut allergy", List.of(14L)));
    }

    @Test
    void testReplaceModifiers_WhenLocked_ShouldReturn409() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/modifiers on a locked item
        // WHY: Items already sent to the kitchen cannot be changed

        // Given - Service throws exception (item locked)
        when(orderItemModifierService.replaceModifiersForOrderItem(eq(1L), any(OrderItemModifiersRequest.class)))
                .thenThrow(new ItemLockedException("Cannot change modifiers on locked item. Item has been sent to kitchen."));

        // When/Then
        mockMvc.perform(put("/api/order-items/1/modifiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modifierIds\":[14]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void testReplaceModifiers_WhenNotFound_ShouldReturn404() throws Exception {
        // WHAT: Test PUT /api/order-items/{id}/modifiers for an item that does not exist

        // Given
        when(orderItemModifierService.replaceModifiersForOrderItem(eq(999L), any(OrderItemModifiersRequest.class)))
                .thenThrow(new ResourceNotFoundException("Order item not found with id: 999"));

        // When/Then
        mockMvc.perform(put("/api/order-items/999/modifiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modifierIds\":[]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testRingInItem_WithAPriceInTheRequest_ShouldIgnoreIt() throws Exception {
        // WHAT: Test POST /api/order-items with a "price" the screen should never send
        // WHY: The request type has no price field at all, so a price in the JSON has nowhere
        //      to go. The service never sees it and works the price out from the menu.

        OrderItem saved = new OrderItem();
        saved.setOrderItemId(5L);
        saved.setPrice(new BigDecimal("17.00"));
        when(orderItemModifierService.ringInItem(any())).thenReturn(saved);

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"menuItemId\":2,\"quantity\":1,\"price\":0.01,\"status\":\"completed\",\"modifierIds\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(17.00));

        // What reached the service: the order, the menu item, the quantity, no note, no modifiers - and nothing else
        verify(orderItemModifierService).ringInItem(
                new com.notapos.dto.OrderItemRequest(1L, 2L, 1, null, java.util.List.of()));
    }

    @Test
    void testRingInItem_WhenTheCheckWasClosedElsewhere_ShouldReturn409WithACode() throws Exception {
        // WHAT: Test POST /api/order-items when the order is no longer open
        // WHY: The order screen checks the code (not the wording) to know it must tell the
        //      server the check was closed and reload the table

        when(orderItemModifierService.ringInItem(any())).thenThrow(
                new com.notapos.exception.OrderNotOpenException("This check was already closed, most likely on another terminal. Nothing was added or sent."));

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":1,\"menuItemId\":2,\"quantity\":1,\"modifierIds\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"))
                .andExpect(jsonPath("$.message").value("This check was already closed, most likely on another terminal. Nothing was added or sent."));
    }

    @Test
    void testSendItemsForOrder_WhenTheCheckWasClosedElsewhere_ShouldReturn409WithACode() throws Exception {
        // WHAT: Test POST /api/order-items/order/{id}/send when the order is no longer open
        // WHY: Same signal to the screen as ringing in

        when(orderItemService.sendItemsForOrder(1L)).thenThrow(
                new com.notapos.exception.OrderNotOpenException("This check was already closed."));

        mockMvc.perform(post("/api/order-items/order/1/send"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"));
    }

    @Test
    void testOtherConflicts_ShouldCarryNoCode() throws Exception {
        // WHAT: Test that an ordinary 409 (a locked item) has no code
        // WHY: The code marks ONE situation. A screen must not mistake "this item already
        //      went to the kitchen" for "this check was closed".

        doThrow(new com.notapos.exception.ItemLockedException("Cannot delete locked item. Item has been sent to kitchen."))
                .when(orderItemService).deleteOrderItem(1L);

        mockMvc.perform(delete("/api/order-items/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.nullValue()));
    }
}
