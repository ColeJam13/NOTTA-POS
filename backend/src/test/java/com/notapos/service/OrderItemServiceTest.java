package com.notapos.service;

import com.notapos.entity.OrderItem;
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
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        List<OrderItem> sentItems = orderItemService.sendItemsForOrder(1L);

        // Then
        assertEquals(1, sentItems.size());
        OrderItem sent = sentItems.get(0);
        assertEquals("limbo", sent.getStatus());
        assertNotNull(sent.getDelayExpiresAt());
        assertFalse(sent.getIsLocked());
        verify(orderItemRepository, times(1)).save(testItem);
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
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        List<OrderItem> lockedItems = orderItemService.lockAndSendExpiredItems();

        // Then
        assertEquals(1, lockedItems.size());
        OrderItem locked = lockedItems.get(0);
        assertTrue(locked.getIsLocked());
        assertEquals("pending", locked.getStatus());
        verify(orderItemRepository, times(1)).save(testItem);
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
}