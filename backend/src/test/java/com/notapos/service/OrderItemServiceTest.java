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
        assertEquals("pending", sent.getStatus());
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
    void testLockAndFireExpiredItems_ShouldLockExpiredItems() {
        // Given
        testItem.setStatus("pending");
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
        assertEquals("fired", locked.getStatus());
        assertNotNull(locked.getFiredAt());
        verify(orderItemRepository, times(1)).save(testItem);
    }

    @Test
    void testFireItemNow_ShouldBypassTimer() {
        // Given
        when(orderItemRepository.findById(1L)).thenReturn(Optional.of(testItem));
        when(orderItemRepository.save(any(OrderItem.class))).thenReturn(testItem);

        // When
        OrderItem fired = orderItemService.sendItemNow(1L);

        // Then
        assertTrue(fired.getIsLocked());
        assertEquals("fired", fired.getStatus());
        assertNotNull(fired.getFiredAt());
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
}