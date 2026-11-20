package com.notapos.controller;

import com.notapos.dto.ActiveOrderItemView;
import com.notapos.dto.ActiveOrderView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.service.ActiveOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for ActiveOrderController.
 *
 * WHAT: Tests GET /api/orders/active using MockMvc (no real server or database)
 * WHY: Make sure the JSON the Active Orders screen reads has the field names
 *      it expects (tableNumber, isQuickOrder, items[].name, items[].status,
 *      items[].modifiers[].name)
 *
 * @author CJ
 */
@WebMvcTest(ActiveOrderController.class)
class ActiveOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActiveOrderService activeOrderService;

    @Test
    void testGetActiveOrders_ShouldReturnOrdersWithTableAndItems() throws Exception {
        // Given - one open order at table W3 with one pending item (with a modifier and a kitchen note)
        ActiveOrderItemView item = new ActiveOrderItemView(
                5L, 2L, "Chicken Cutty", 1, new BigDecimal("17.00"), "pending", "no onions",
                List.of(new OrderItemModifierView(5L, 16L, "Hashbrowns", new BigDecimal("0.00"))),
                1L, LocalDateTime.of(2026, 1, 1, 12, 5));          // Made at station 1 (the kitchen), sent at 12:05
        ActiveOrderView order = new ActiveOrderView(
                1L, 3L, "W3", false, "Sarah", LocalDateTime.of(2026, 1, 1, 12, 0), List.of(item));
        when(activeOrderService.getActiveOrders()).thenReturn(List.of(order));

        // When/Then
        mockMvc.perform(get("/api/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(1))
                .andExpect(jsonPath("$[0].tableNumber").value("W3"))
                .andExpect(jsonPath("$[0].isQuickOrder").value(false))
                .andExpect(jsonPath("$[0].serverName").value("Sarah"))
                .andExpect(jsonPath("$[0].items.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].orderItemId").value(5))
                .andExpect(jsonPath("$[0].items[0].name").value("Chicken Cutty"))
                .andExpect(jsonPath("$[0].items[0].status").value("pending"))
                .andExpect(jsonPath("$[0].items[0].specialInstructions").value("no onions"))
                .andExpect(jsonPath("$[0].items[0].prepStationId").value(1))
                .andExpect(jsonPath("$[0].items[0].sentAt").value("2026-01-01T12:05:00"))
                .andExpect(jsonPath("$[0].items[0].modifiers.length()").value(1))
                .andExpect(jsonPath("$[0].items[0].modifiers[0].modifierId").value(16))
                .andExpect(jsonPath("$[0].items[0].modifiers[0].name").value("Hashbrowns"));

        verify(activeOrderService, times(1)).getActiveOrders();
    }

    @Test
    void testGetActiveOrders_WhenNoneOpen_ShouldReturnEmptyArray() throws Exception {
        // Given
        when(activeOrderService.getActiveOrders()).thenReturn(List.of());

        // When/Then
        mockMvc.perform(get("/api/orders/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
