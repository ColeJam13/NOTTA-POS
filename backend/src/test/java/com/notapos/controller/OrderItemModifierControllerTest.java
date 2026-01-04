package com.notapos.controller;

import com.notapos.entity.OrderItemModifier;
import com.notapos.service.OrderItemModifierService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for OrderItemModifierController.
 * 
 * Tests REST API endpoints for tracking which modifiers were selected for order items.
 * Records customer selections with pricing captured at time of order.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@WebMvcTest(OrderItemModifierController.class)
class OrderItemModifierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderItemModifierService orderItemModifierService;

    private OrderItemModifier testModifier;

    @BeforeEach
    void setUp() {
        testModifier = new OrderItemModifier();
        testModifier.setOrderItemModifierId(1L);
        testModifier.setOrderItemId(1L);
        testModifier.setModifierId(2L);
        testModifier.setPriceAdjustment(new BigDecimal("2.00"));
    }

    @Test
    void testGetAllOrderItemModifiers_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/order-item-modifiers
        // WHY: Retrieve all modifier selections
        
        List<OrderItemModifier> modifiers = Arrays.asList(testModifier);
        when(orderItemModifierService.getAllOrderItemModifiers()).thenReturn(modifiers);

        mockMvc.perform(get("/api/order-item-modifiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderItemId").value(1))
                .andExpect(jsonPath("$[0].modifierId").value(2));

        verify(orderItemModifierService).getAllOrderItemModifiers();
    }

    @Test
    void testGetOrderItemModifierById_WhenExists_ShouldReturnModifier() throws Exception {
        // WHAT: Test GET /api/order-item-modifiers/{id}
        // WHY: Retrieve specific modifier selection
        
        when(orderItemModifierService.getOrderItemModifierById(1L)).thenReturn(Optional.of(testModifier));

        mockMvc.perform(get("/api/order-item-modifiers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemModifierId").value(1))
                .andExpect(jsonPath("$.priceAdjustment").value(2.00));

        verify(orderItemModifierService).getOrderItemModifierById(1L);
    }

    @Test
    void testGetOrderItemModifierById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/order-item-modifiers/{id} when not found
        // WHY: Handle missing modifiers
        
        when(orderItemModifierService.getOrderItemModifierById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/order-item-modifiers/999"))
                .andExpect(status().isNotFound());

        verify(orderItemModifierService).getOrderItemModifierById(999L);
    }

    @Test
    void testGetModifiersForOrderItem_ShouldReturnModifiers() throws Exception {
        // WHAT: Test GET /api/order-item-modifiers/order-item/{orderItemId}
        // WHY: Show what customizations customer selected for their Chicken Cutty
        
        List<OrderItemModifier> modifiers = Arrays.asList(testModifier);
        when(orderItemModifierService.getModifiersForOrderItem(1L)).thenReturn(modifiers);

        mockMvc.perform(get("/api/order-item-modifiers/order-item/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderItemId").value(1));

        verify(orderItemModifierService).getModifiersForOrderItem(1L);
    }

    @Test
    void testGetOrderItemsWithModifier_ShouldReturnOrderItems() throws Exception {
        // WHAT: Test GET /api/order-item-modifiers/modifier/{modifierId}
        // WHY: Analytics - how many orders included "Add Bacon"
        
        List<OrderItemModifier> modifiers = Arrays.asList(testModifier);
        when(orderItemModifierService.getOrderItemsWithModifier(2L)).thenReturn(modifiers);

        mockMvc.perform(get("/api/order-item-modifiers/modifier/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modifierId").value(2));

        verify(orderItemModifierService).getOrderItemsWithModifier(2L);
    }

    @Test
    void testCreateOrderItemModifier_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/order-item-modifiers
        // WHY: Record customer's modifier selection
        
        when(orderItemModifierService.createOrderItemModifier(any(OrderItemModifier.class))).thenReturn(testModifier);

        mockMvc.perform(post("/api/order-item-modifiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderItemId\":1,\"modifierId\":2,\"priceAdjustment\":2.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderItemId").value(1));

        verify(orderItemModifierService).createOrderItemModifier(any(OrderItemModifier.class));
    }

    @Test
    void testDeleteOrderItemModifier_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/order-item-modifiers/{id}
        // WHY: Remove modifier from order item
        
        doNothing().when(orderItemModifierService).deleteOrderItemModifier(1L);

        mockMvc.perform(delete("/api/order-item-modifiers/1"))
                .andExpect(status().isNoContent());

        verify(orderItemModifierService).deleteOrderItemModifier(1L);
    }
}