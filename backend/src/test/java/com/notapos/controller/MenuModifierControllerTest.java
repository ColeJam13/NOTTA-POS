package com.notapos.controller;

import com.notapos.dto.MenuItemModifiersView;
import com.notapos.dto.ModifierGroupView;
import com.notapos.dto.ModifierOptionView;
import com.notapos.service.MenuModifierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for MenuModifierController.
 *
 * WHAT: Tests GET /api/menu-items/modifier-groups using MockMvc (no real server or database)
 * WHY: Make sure the JSON the modifier picker reads has the field names it
 *      expects (menuItemId, groups[].isRequired, groups[].maxSelections,
 *      groups[].modifiers[].priceAdjustment)
 *
 * @author CJ
 */
@ControllerTest(MenuModifierController.class)
class MenuModifierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuModifierService menuModifierService;

    @Test
    void testGetModifierGroupsForMenu_ShouldReturnGroupsWithTheirChoices() throws Exception {
        // Given - Combo Plate (item 1) has one required group with two choices
        ModifierGroupView bread = new ModifierGroupView(
                2L, "Bread Choice", "Choose your bread", true, 1,
                List.of(
                        new ModifierOptionView(8L, "White Toast", new BigDecimal("0.00")),
                        new ModifierOptionView(14L, "French Toast", new BigDecimal("2.00"))));
        when(menuModifierService.getModifierGroupsForMenu())
                .thenReturn(List.of(new MenuItemModifiersView(1L, List.of(bread))));

        // When/Then
        mockMvc.perform(get("/api/menu-items/modifier-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].menuItemId").value(1))
                .andExpect(jsonPath("$[0].groups.length()").value(1))
                .andExpect(jsonPath("$[0].groups[0].modifierGroupId").value(2))
                .andExpect(jsonPath("$[0].groups[0].name").value("Bread Choice"))
                .andExpect(jsonPath("$[0].groups[0].isRequired").value(true))
                .andExpect(jsonPath("$[0].groups[0].maxSelections").value(1))
                .andExpect(jsonPath("$[0].groups[0].modifiers.length()").value(2))
                .andExpect(jsonPath("$[0].groups[0].modifiers[1].modifierId").value(14))
                .andExpect(jsonPath("$[0].groups[0].modifiers[1].name").value("French Toast"))
                .andExpect(jsonPath("$[0].groups[0].modifiers[1].priceAdjustment").value(2.00));

        verify(menuModifierService, times(1)).getModifierGroupsForMenu();
    }

    @Test
    void testGetModifierGroupsForMenu_WhenNoItemHasModifiers_ShouldReturnEmptyArray() throws Exception {
        // Given
        when(menuModifierService.getModifierGroupsForMenu()).thenReturn(List.of());

        // When/Then
        mockMvc.perform(get("/api/menu-items/modifier-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
