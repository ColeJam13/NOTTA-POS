package com.notapos.service;

import com.notapos.entity.OrderItem;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderItemService.
 * 
 * Tests the delay timer logic - the signature feature of Nota-POS.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class OrderItemServiceTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderItemModifierRepository orderItemModifierRepository;

    @InjectMocks
    private OrderItemService orderItemService;

    private OrderItem testItem;

    @BeforeEach
    void setUp() {                                          // Create a test order item
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
    void testCreateOrderItem_ShouldStartAsDraft() {
        // Given
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        OrderItem created = orderItemService.createOrderItem(testItem);

        // Then
        assertEquals("draft", created.getStatus());
        assertFalse(created.getIsLocked());
        verify(orderItemRepository, times(1)).save(testItem);
    }

    @Test
    void testSendItemsForOrder_ShouldStartTimer() {
        // Given
        List<OrderItem> draftItems = Arrays.asList(testItem);
        when(orderItemRepository.findByOrderIdAndStatus(1L, "draft")).thenReturn(draftItems);

        // When
        List<OrderItem> sentItems = orderItemService.sendItemsForOrder(1L);

        // Then
        assertEquals(1, sentItems.size());
        OrderItem sent = sentItems.get(0);
        assertEquals("limbo", sent.getStatus());
        assertNotNull(sent.getDelayExpiresAt());
        assertFalse(sent.getIsLocked());
        verify(orderItemRepository, times(1)).saveAll(draftItems);       // One batched save, not one save per item
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testUpdateOrderItem_WhenPending_ShouldResetTimer() {
        // Given
        testItem.setStatus("pending");
        testItem.setDelayExpiresAt(LocalDateTime.now().plusSeconds(5)); // Expiring soon
        
        OrderItem updatedItem = new OrderItem();
        updatedItem.setQuantity(2);
        updatedItem.setPrice(new BigDecimal("17.00"));
        
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        OrderItem result = orderItemService.updateOrderItem(1L, updatedItem);

        // Then
        assertEquals("pending", result.getStatus());
        assertTrue(result.getDelayExpiresAt().isAfter(LocalDateTime.now().plusSeconds(10)));
        verify(orderItemRepository, times(1)).save(testItem);
    }

    @Test
    void testUpdateOrderItem_WhenLocked_ShouldThrowException() {
        // Given
        testItem.setIsLocked(true);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderItemService.updateOrderItem(1L, new OrderItem());
        });
        
        assertTrue(exception.getMessage().contains("Cannot update locked item"));
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testLockAndSendExpiredItems_ShouldLockExpiredItems() {
        // Given
        testItem.setStatus("limbo");
        testItem.setDelayExpiresAt(LocalDateTime.now().minusSeconds(5)); // Already expired
        testItem.setIsLocked(false);
        
        List<OrderItem> expiredItems = Arrays.asList(testItem);
        when(orderItemRepository.findExpiredUnlockedItems(any(LocalDateTime.class), eq(false)))
                .thenReturn(expiredItems);

        // When
        List<OrderItem> lockedItems = orderItemService.lockAndSendExpiredItems();

        // Then
        assertEquals(1, lockedItems.size());
        OrderItem locked = lockedItems.get(0);
        assertTrue(locked.getIsLocked());
        assertEquals("pending", locked.getStatus());
        verify(orderItemRepository, times(1)).saveAll(expiredItems);     // One batched save, not one save per item
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testSendOrderNow_ShouldLockAllDraftAndLimboItems() {
        // Given - one draft item and one limbo item on the same order
        OrderItem draftItem = new OrderItem();
        draftItem.setOrderItemId(10L);
        draftItem.setStatus("draft");
        draftItem.setIsLocked(false);

        OrderItem limboItem = new OrderItem();
        limboItem.setOrderItemId(11L);
        limboItem.setStatus("limbo");
        limboItem.setIsLocked(false);

        when(orderItemRepository.findByOrderIdAndStatus(1L, "draft")).thenReturn(Arrays.asList(draftItem));
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(Arrays.asList(limboItem));

        // When
        List<OrderItem> sentItems = orderItemService.sendOrderNow(1L);

        // Then - both are locked and pending, saved together in one batch
        assertEquals(2, sentItems.size());
        for (OrderItem sent : sentItems) {
            assertTrue(sent.getIsLocked());
            assertEquals("pending", sent.getStatus());
        }
        verify(orderItemRepository, times(1)).saveAll(sentItems);
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testSendOrderNow_WhenNothingToSend_ShouldReturnEmptyList() {
        // Given - order has no draft or limbo items
        when(orderItemRepository.findByOrderIdAndStatus(1L, "draft")).thenReturn(List.of());
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(List.of());

        // When
        List<OrderItem> sentItems = orderItemService.sendOrderNow(1L);

        // Then
        assertTrue(sentItems.isEmpty());
    }

    @Test
    void testSendItemNow_ShouldBypassTimer() {
        // Given
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        OrderItem sent = orderItemService.sendItemNow(1L);

        // Then
        assertTrue(sent.getIsLocked());
        assertEquals("pending", sent.getStatus());
        verify(orderItemRepository, times(1)).save(testItem);
    }

    @Test
    void testSendOrderNow_ShouldStampDraftsWithASendTimeAndKeepAnExistingOne() {
        // WHAT: Test the send time on items sent straight to the kitchen
        // WHY: The kitchen display shows how long a ticket has waited, counted from when it
        //      was sent. A draft that skips the edit window never had a send time.

        // Given - a draft (never sent), and an item in limbo that was sent 10 seconds ago
        LocalDateTime sentEarlier = LocalDateTime.now().minusSeconds(10);
        OrderItem inLimbo = new OrderItem();
        inLimbo.setOrderItemId(2L);
        inLimbo.setOrderId(1L);
        inLimbo.setStatus("limbo");
        inLimbo.setIsLocked(false);
        inLimbo.setSentAt(sentEarlier);

        when(orderItemRepository.findByOrderIdAndStatus(1L, "draft")).thenReturn(Arrays.asList(testItem));
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(Arrays.asList(inLimbo));

        // When
        orderItemService.sendOrderNow(1L);

        // Then - the draft gets a send time; the item already sent keeps its own
        assertNotNull(testItem.getSentAt());
        assertEquals(sentEarlier, inLimbo.getSentAt());
        assertEquals("pending", testItem.getStatus());
        assertEquals("pending", inLimbo.getStatus());
    }

    @Test
    void testSendItemNow_ShouldStampASendTimeWhenThereIsNone() {
        // WHAT: Test the send time on a single item sent straight to the kitchen
        // WHY: Same as above, for the one-item version

        // Given - a draft with no send time
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        OrderItem sent = orderItemService.sendItemNow(1L);

        // Then
        assertNotNull(sent.getSentAt());
        assertTrue(sent.getIsLocked());
    }

    @Test
    void testDeleteOrderItem_WhenLocked_ShouldThrowException() {
        // Given
        testItem.setIsLocked(true);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderItemService.deleteOrderItem(1L);
        });
        
        assertTrue(exception.getMessage().contains("Cannot delete locked item"));
        verify(orderItemRepository, never()).deleteById(any());
        verify(orderItemModifierRepository, never()).deleteByOrderItemId(any());   // A locked item keeps its modifiers too
    }

    @Test
    void testGetAllOrderItems_ShouldReturnAllItems() {
        // WHAT: Test getting all order items in system
        // WHY: Retrieve complete list of items
        
        // Given
        OrderItem item2 = new OrderItem();
        item2.setOrderItemId(2L);
        List<OrderItem> allItems = Arrays.asList(testItem, item2);
        when(orderItemRepository.findAll()).thenReturn(allItems);

        // When
        List<OrderItem> result = orderItemService.getAllOrderItems();

        // Then
        assertEquals(2, result.size());
        verify(orderItemRepository, times(1)).findAll();
    }

    @Test
    void testGetOrderItemById_WhenExists_ShouldReturnItem() {
        // WHAT: Test getting specific order item by ID
        // WHY: Retrieve individual item details
        
        // Given
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // When
        Optional<OrderItem> result = orderItemService.getOrderItemById(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getOrderItemId());
        verify(orderItemRepository, times(1)).findById(1L);
    }

    @Test
    void testGetOrderItemById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test getting non-existent order item
        // WHY: Handle missing items gracefully
        
        // Given
        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        Optional<OrderItem> result = orderItemService.getOrderItemById(999L);

        // Then
        assertFalse(result.isPresent());
        verify(orderItemRepository, times(1)).findById(999L);
    }

    @Test
    void testGetItemsByOrder_ShouldReturnOrderItems() {
        // WHAT: Test getting all items for specific order
        // WHY: Display complete order ticket
        
        // Given
        OrderItem item2 = new OrderItem();
        item2.setOrderId(1L);
        List<OrderItem> orderItems = Arrays.asList(testItem, item2);
        when(orderItemRepository.findByOrderId(1L)).thenReturn(orderItems);

        // When
        List<OrderItem> result = orderItemService.getItemsByOrder(1L);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(item -> item.getOrderId().equals(1L)));
        verify(orderItemRepository, times(1)).findByOrderId(1L);
    }

    @Test
    void testGetLimboItemsByOrder_ShouldReturnLimboItems() {
        // WHAT: Test getting items in limbo status (timer active)
        // WHY: Show items currently in delay window
        
        // Given
        testItem.setStatus("limbo");
        List<OrderItem> limboItems = Arrays.asList(testItem);
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(limboItems);

        // When
        List<OrderItem> result = orderItemService.getLimboItemsByOrder(1L);

        // Then
        assertEquals(1, result.size());
        assertEquals("limbo", result.get(0).getStatus());
        verify(orderItemRepository, times(1)).findByOrderIdAndStatus(1L, "limbo");
    }

    @Test
    void testGetPendingItemsByOrder_ShouldReturnPendingItems() {
        // WHAT: Test getting pending items for order
        // WHY: Show items sent to kitchen
        
        // Given
        testItem.setStatus("pending");
        List<OrderItem> pendingItems = Arrays.asList(testItem);
        when(orderItemRepository.findPendingItemsByOrder(1L, "pending")).thenReturn(pendingItems);

        // When
        List<OrderItem> result = orderItemService.getPendingItemsByOrder(1L);

        // Then
        assertEquals(1, result.size());
        assertEquals("pending", result.get(0).getStatus());
        verify(orderItemRepository, times(1)).findPendingItemsByOrder(1L, "pending");
    }

    @Test
    void testGetItemsByStatus_ShouldReturnFilteredItems() {
        // WHAT: Test getting items filtered by status
        // WHY: Show all items in specific state across all orders
        
        // Given
        testItem.setStatus("fired");
        OrderItem item2 = new OrderItem();
        item2.setStatus("fired");
        List<OrderItem> firedItems = Arrays.asList(testItem, item2);
        when(orderItemRepository.findByStatus("fired")).thenReturn(firedItems);

        // When
        List<OrderItem> result = orderItemService.getItemsByStatus("fired");

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(item -> "fired".equals(item.getStatus())));
        verify(orderItemRepository, times(1)).findByStatus("fired");
    }

    @Test
    void testStartItem_ShouldMarkItemAsFired() {
        // WHAT: Test kitchen starting to cook item
        // WHY: Mark item as fired when cooking begins
        
        // Given
        testItem.setStatus("pending");
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        OrderItem result = orderItemService.startItem(1L);

        // Then
        assertEquals("fired", result.getStatus());
        assertNotNull(result.getFiredAt());
        verify(orderItemRepository, times(1)).save(testItem);
    }

    @Test
    void testStartItem_WhenNotFound_ShouldThrowException() {
        // WHAT: Test starting non-existent item
        // WHY: Handle missing items gracefully
        
        // Given
        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderItemService.startItem(999L);
        });
        
        assertTrue(exception.getMessage().contains("Order item not found"));
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testCompleteItem_ShouldMarkItemAsCompleted() {
        // WHAT: Test marking item as completed
        // WHY: Mark item done when food is ready
        
        // Given
        testItem.setStatus("fired");
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        OrderItem result = orderItemService.completeItem(1L);

        // Then
        assertEquals("completed", result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(orderItemRepository, times(1)).save(testItem);
    }

    @Test
    void testCompleteItem_WhenNotFound_ShouldThrowException() {
        // WHAT: Test completing non-existent item
        // WHY: Handle missing items gracefully
        
        // Given
        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            orderItemService.completeItem(999L);
        });
        
        assertTrue(exception.getMessage().contains("Order item not found"));
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testDeleteOrderItem_WhenNotLocked_ShouldDelete() {
        // WHAT: Test deleting unlocked order item
        // WHY: Remove items that haven't been sent yet
        
        // Given
        testItem.setIsLocked(false);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        doNothing().when(orderItemRepository).deleteById(1L);

        // When
        orderItemService.deleteOrderItem(1L);

        // Then
        verify(orderItemRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteOrderItem_ShouldAlsoDeleteItsModifiers() {
        // WHAT: Test that removing an item removes its modifier rows
        // WHY: Tables are linked by plain ID columns, so nothing else would clean them up

        // Given
        testItem.setIsLocked(false);
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // When
        orderItemService.deleteOrderItem(1L);

        // Then
        verify(orderItemModifierRepository, times(1)).deleteByOrderItemId(1L);
        verify(orderItemRepository, times(1)).deleteById(1L);
    }

    @Test
    void testSendItemsForOrder_ShouldGiveItemsAlreadyCountingDownTheSameFreshWindow() {
        // WHAT: Test sending a new item while an earlier item is still in its edit window
        // WHY: The screen shows one countdown for the whole order and restarts it when an
        //      item is added. The earlier item must not lock while the screen says "15 seconds".

        // Given - one new draft, and one item sent earlier with 3 seconds left
        OrderItem earlier = new OrderItem();
        earlier.setOrderItemId(2L);
        earlier.setOrderId(1L);
        earlier.setStatus("limbo");
        earlier.setDelaySeconds(15);
        earlier.setIsLocked(false);
        earlier.setDelayExpiresAt(LocalDateTime.now().plusSeconds(3));

        when(orderItemRepository.findByOrderIdAndStatus(1L, "draft")).thenReturn(Arrays.asList(testItem));
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(Arrays.asList(earlier));

        // When
        List<OrderItem> sentItems = orderItemService.sendItemsForOrder(1L);

        // Then - only the new item is reported as sent...
        assertEquals(1, sentItems.size());
        assertEquals("limbo", testItem.getStatus());

        // ...but both now expire at the same moment, a full window from now
        assertEquals(testItem.getDelayExpiresAt(), earlier.getDelayExpiresAt());
        assertTrue(earlier.getDelayExpiresAt().isAfter(LocalDateTime.now().plusSeconds(14)));
        verify(orderItemRepository, times(1)).saveAll(Arrays.asList(testItem, earlier));
    }

    @Test
    void testSendItemsForOrder_WhenNothingToSend_ShouldLeaveARunningWindowAlone() {
        // WHAT: Test pressing Send when there are no drafts
        // WHY: An empty Send must not push back the items that are already counting down

        // Given - no drafts
        when(orderItemRepository.findByOrderIdAndStatus(1L, "draft")).thenReturn(List.of());

        // When
        List<OrderItem> sentItems = orderItemService.sendItemsForOrder(1L);

        // Then
        assertTrue(sentItems.isEmpty());
        verify(orderItemRepository, never()).findByOrderIdAndStatus(1L, "limbo");
        verify(orderItemRepository, never()).saveAll(any());
    }

    @Test
    void testRestartEditWindow_ShouldPushBackEveryItemStillCountingDown() {
        // WHAT: Test restarting the edit window for an order
        // WHY: A change during the window gives the whole order a fresh 15 seconds,
        //      matching the single countdown the server sees on screen

        // Given - two items in limbo, about to expire
        testItem.setStatus("limbo");
        testItem.setDelayExpiresAt(LocalDateTime.now().plusSeconds(2));

        OrderItem second = new OrderItem();
        second.setOrderItemId(2L);
        second.setOrderId(1L);
        second.setStatus("limbo");
        second.setDelaySeconds(15);
        second.setDelayExpiresAt(LocalDateTime.now().plusSeconds(2));

        List<OrderItem> limboItems = Arrays.asList(testItem, second);
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(limboItems);

        // When
        orderItemService.restartEditWindow(1L);

        // Then - both expire a full window from now (15 seconds + 1 for rendering)
        for (OrderItem item : limboItems) {
            assertTrue(item.getDelayExpiresAt().isAfter(LocalDateTime.now().plusSeconds(14)));
            assertTrue(item.getDelayExpiresAt().isBefore(LocalDateTime.now().plusSeconds(17)));
            assertEquals("limbo", item.getStatus());
        }
        verify(orderItemRepository, times(1)).saveAll(limboItems);
    }

    @Test
    void testRestartEditWindow_WhenNoWindowIsRunning_ShouldDoNothing() {
        // WHAT: Test restarting the window on an order with nothing in limbo
        // WHY: Locked items must never be given a new window

        // Given
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(List.of());

        // When
        orderItemService.restartEditWindow(1L);

        // Then
        verify(orderItemRepository, never()).saveAll(any());
    }

    @Test
    void testDeleteOrderItem_WhenInEditWindow_ShouldRestartTheWindowForTheRest() {
        // WHAT: Test removing an item while the countdown is running
        // WHY: The screen restarts its countdown on a removal; the remaining items must match

        // Given - the item being removed is in limbo, and one other item is too
        testItem.setStatus("limbo");
        OrderItem remaining = new OrderItem();
        remaining.setOrderItemId(2L);
        remaining.setOrderId(1L);
        remaining.setStatus("limbo");
        remaining.setDelaySeconds(15);
        remaining.setDelayExpiresAt(LocalDateTime.now().plusSeconds(2));

        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(orderItemRepository.findByOrderIdAndStatus(1L, "limbo")).thenReturn(Arrays.asList(remaining));

        // When
        orderItemService.deleteOrderItem(1L);

        // Then
        verify(orderItemRepository, times(1)).deleteById(1L);
        assertTrue(remaining.getDelayExpiresAt().isAfter(LocalDateTime.now().plusSeconds(14)));
    }

    @Test
    void testDeleteOrderItem_WhenDraft_ShouldNotTouchTheEditWindow() {
        // WHAT: Test removing an item that was never sent
        // WHY: Removing a draft is not a change to anything the kitchen is waiting on

        // Given - testItem is a draft (from setUp)
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));

        // When
        orderItemService.deleteOrderItem(1L);

        // Then
        verify(orderItemRepository, never()).findByOrderIdAndStatus(any(), any());
        verify(orderItemRepository, never()).saveAll(any());
    }
}
