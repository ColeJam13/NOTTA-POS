package com.notapos.service;

import com.notapos.dto.ActiveOrderItemView;
import com.notapos.dto.ActiveOrderView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ActiveOrderService.
 *
 * WHAT: Tests that open orders are matched up with their table and items
 * WHY: This one response replaces four separate downloads on the Active
 *      Orders screen, so the matching logic has to be right
 *
 * @author CJ
 */
@ExtendWith(MockitoExtension.class)
class ActiveOrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private OrderItemModifierService orderItemModifierService;

    @InjectMocks
    private ActiveOrderService activeOrderService;

    // ---- small builders to keep the tests readable ----

    private Order order(Long orderId, Long tableId) {
        Order order = new Order();
        order.setOrderId(orderId);
        order.setTableId(tableId);
        order.setStatus("open");
        order.setServerName("Sarah");
        return order;
    }

    private OrderItem item(Long orderItemId, Long orderId, Long menuItemId, String status) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(orderItemId);
        item.setOrderId(orderId);
        item.setMenuItemId(menuItemId);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("17.00"));
        item.setStatus(status);
        return item;
    }

    private MenuItem menuItem(Long menuItemId, String name) {
        MenuItem menuItem = new MenuItem();
        menuItem.setMenuItemId(menuItemId);
        menuItem.setName(name);
        return menuItem;
    }

    private RestaurantTable table(Long tableId, String tableNumber) {
        RestaurantTable table = new RestaurantTable();
        table.setTableId(tableId);
        table.setTableNumber(tableNumber);
        table.setIsQuickOrder(false);
        return table;
    }

    @Test
    void testGetActiveOrders_ShouldMatchItemsAndTablesToTheirOrders() {
        // Given - two open orders; items arrive from the database out of order
        when(orderRepository.findByStatus("open"))
                .thenReturn(Arrays.asList(order(2L, 20L), order(1L, 10L)));
        when(orderItemRepository.findByOrderIdIn(anyCollection()))
                .thenReturn(Arrays.asList(
                        item(102L, 1L, 501L, "pending"),
                        item(201L, 2L, 502L, "fired"),
                        item(101L, 1L, 502L, "limbo")));
        when(menuItemRepository.findAllById(any()))
                .thenReturn(Arrays.asList(menuItem(501L, "Chicken Cutty"), menuItem(502L, "Fries")));
        when(tableRepository.findAllById(any()))
                .thenReturn(Arrays.asList(table(10L, "W3"), table(20L, "B1")));

        // When
        List<ActiveOrderView> result = activeOrderService.getActiveOrders();

        // Then - orders sorted oldest first
        assertEquals(2, result.size());
        ActiveOrderView first = result.get(0);
        ActiveOrderView second = result.get(1);
        assertEquals(1L, first.orderId());
        assertEquals(2L, second.orderId());

        // Each order got its own table
        assertEquals("W3", first.tableNumber());
        assertEquals("B1", second.tableNumber());
        assertEquals("Sarah", first.serverName());

        // Each order got only its own items, sorted by item ID, with names filled in
        assertEquals(2, first.items().size());
        assertEquals(101L, first.items().get(0).orderItemId());
        assertEquals("Fries", first.items().get(0).name());
        assertEquals("limbo", first.items().get(0).status());
        assertEquals(102L, first.items().get(1).orderItemId());
        assertEquals("Chicken Cutty", first.items().get(1).name());
        assertEquals(1, second.items().size());
        assertEquals("Fries", second.items().get(0).name());

        // The whole point: ONE items query for all orders, not one per order
        verify(orderItemRepository, times(1)).findByOrderIdIn(anyCollection());
        verify(orderItemRepository, never()).findByOrderId(any());
    }

    @Test
    void testGetActiveOrders_WhenNoOpenOrders_ShouldSkipOtherQueries() {
        // Given
        when(orderRepository.findByStatus("open")).thenReturn(List.of());

        // When
        List<ActiveOrderView> result = activeOrderService.getActiveOrders();

        // Then - empty list and no wasted queries
        assertTrue(result.isEmpty());
        verifyNoInteractions(orderItemRepository, menuItemRepository, tableRepository, orderItemModifierService);
    }

    @Test
    void testGetActiveOrders_WhenMenuItemOrTableMissing_ShouldStillReturnOrder() {
        // Given - the menu item and the table were deleted after the order was placed
        when(orderRepository.findByStatus("open")).thenReturn(Arrays.asList(order(1L, 10L)));
        when(orderItemRepository.findByOrderIdIn(anyCollection()))
                .thenReturn(Arrays.asList(item(101L, 1L, 999L, "pending")));
        when(menuItemRepository.findAllById(any())).thenReturn(List.of());
        when(tableRepository.findAllById(any())).thenReturn(List.of());

        // When
        List<ActiveOrderView> result = activeOrderService.getActiveOrders();

        // Then - a placeholder name instead of a crash
        assertEquals(1, result.size());
        assertNull(result.get(0).tableNumber());
        assertEquals("Item 999", result.get(0).items().get(0).name());
    }

    @Test
    void testGetActiveOrders_WhenOrderHasNoItems_ShouldReturnEmptyItemList() {
        // Given - an order was opened but nothing has been rung in yet
        when(orderRepository.findByStatus("open")).thenReturn(Arrays.asList(order(1L, 10L)));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of());
        when(menuItemRepository.findAllById(any())).thenReturn(List.of());
        when(tableRepository.findAllById(any())).thenReturn(Arrays.asList(table(10L, "W3")));

        // When
        List<ActiveOrderView> result = activeOrderService.getActiveOrders();

        // Then
        assertEquals(1, result.size());
        assertTrue(result.get(0).items().isEmpty());
    }

    @Test
    void testGetActiveOrders_ShouldAttachModifiersAndKitchenNoteToEachItem() {
        // WHAT: Test that the kitchen ticket carries each item's modifiers and note
        // WHY: "Combo Plate" alone is not enough to cook it - the kitchen needs the choices

        // Given - a Combo Plate with two modifiers and a note, and plain Fries
        OrderItem comboPlate = item(101L, 1L, 501L, "pending");
        comboPlate.setSpecialInstructions("nut allergy");
        when(orderRepository.findByStatus("open")).thenReturn(Arrays.asList(order(1L, 10L)));
        when(orderItemRepository.findByOrderIdIn(anyCollection()))
                .thenReturn(Arrays.asList(comboPlate, item(102L, 1L, 502L, "pending")));
        when(menuItemRepository.findAllById(any()))
                .thenReturn(Arrays.asList(menuItem(501L, "Combo Plate"), menuItem(502L, "Fries")));
        when(tableRepository.findAllById(any())).thenReturn(Arrays.asList(table(10L, "W3")));
        when(orderItemModifierService.getModifierViewsForOrderItems(List.of(101L, 102L)))
                .thenReturn(Map.of(101L, List.of(
                        new OrderItemModifierView(101L, 14L, "French Toast", new BigDecimal("2.00")),
                        new OrderItemModifierView(101L, 16L, "Hashbrowns", new BigDecimal("0.00")))));

        // When
        List<ActiveOrderView> result = activeOrderService.getActiveOrders();

        // Then - the Combo Plate has its modifiers and note
        ActiveOrderItemView comboPlateView = result.get(0).items().get(0);
        assertEquals("Combo Plate", comboPlateView.name());
        assertEquals("nut allergy", comboPlateView.specialInstructions());
        assertEquals(2, comboPlateView.modifiers().size());
        assertEquals("French Toast", comboPlateView.modifiers().get(0).name());
        assertEquals("Hashbrowns", comboPlateView.modifiers().get(1).name());

        // The Fries have an empty list (never null), so the screen can loop over it safely
        ActiveOrderItemView friesView = result.get(0).items().get(1);
        assertNull(friesView.specialInstructions());
        assertTrue(friesView.modifiers().isEmpty());

        // ONE modifiers lookup for every item on every order
        verify(orderItemModifierService, times(1)).getModifierViewsForOrderItems(anyCollection());
    }

    @Test
    void testGetActiveOrders_ShouldCarryEachItemsPrepStationAndSendTime() {
        // WHAT: Test that each item says which station makes it and when it was sent
        // WHY: The kitchen view shows the bar only drinks and the kitchen only food, and
        //      shows how long each ticket has been waiting

        // Given - A latte made at the bar (station 2), sent at 12:05; and a draft with no send time
        LocalDateTime sentAt = LocalDateTime.of(2026, 1, 1, 12, 5);
        OrderItem latte = item(100L, 1L, 3L, "pending");
        latte.setSentAt(sentAt);
        OrderItem draft = item(101L, 1L, 9L, "draft");               // Menu item 9 is not in the menu lookup below

        MenuItem latteMenuItem = menuItem(3L, "Latte");
        latteMenuItem.setPrepStationId(2L);

        when(orderRepository.findByStatus("open")).thenReturn(List.of(order(1L, 10L)));
        when(orderItemRepository.findByOrderIdIn(List.of(1L))).thenReturn(List.of(latte, draft));
        when(menuItemRepository.findAllById(any())).thenReturn(List.of(latteMenuItem));
        when(tableRepository.findAllById(any())).thenReturn(List.of());

        // When
        List<ActiveOrderView> result = activeOrderService.getActiveOrders();

        // Then
        ActiveOrderItemView latteView = result.get(0).items().get(0);
        assertEquals(2L, latteView.prepStationId());
        assertEquals(sentAt, latteView.sentAt());

        // An item whose menu item is gone has no station, and a draft has no send time
        ActiveOrderItemView draftView = result.get(0).items().get(1);
        assertNull(draftView.prepStationId());
        assertNull(draftView.sentAt());
    }
}
