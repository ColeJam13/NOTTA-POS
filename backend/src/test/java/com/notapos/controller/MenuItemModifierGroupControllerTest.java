package com.notapos.controller;

import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.service.MenuItemModifierGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for MenuItemModifierGroupController.
 * 
 * Tests REST API endpoints for linking menu items to modifier groups.
 * Junction table that defines which modifier groups apply to which menu items.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@WebMvcTest(MenuItemModifierGroupController.class)
class MenuItemModifierGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MenuItemModifierGroupService menuItemModifierGroupService;

    private MenuItemModifierGroup testLink;

    @BeforeEach
    void setUp() {
        testLink = new MenuItemModifierGroup();
        testLink.setMenuItemModifierGroupId(1L);
        testLink.setMenuItemId(1L);
        testLink.setModifierGroupId(1L);
    }

    @Test
    void testGetAllLinks_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/menu-item-modifier-groups
        // WHY: Retrieve all menu item to modifier group links
        
        List<MenuItemModifierGroup> links = Arrays.asList(testLink);
        when(menuItemModifierGroupService.getAllLinks()).thenReturn(links);

        mockMvc.perform(get("/api/menu-item-modifier-groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].menuItemId").value(1))
                .andExpect(jsonPath("$[0].modifierGroupId").value(1));

        verify(menuItemModifierGroupService).getAllLinks();
    }

    @Test
    void testGetLinkById_WhenExists_ShouldReturnLink() throws Exception {
        // WHAT: Test GET /api/menu-item-modifier-groups/{id}
        // WHY: Retrieve specific link
        
        when(menuItemModifierGroupService.getLinkById(1L)).thenReturn(Optional.of(testLink));

        mockMvc.perform(get("/api/menu-item-modifier-groups/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuItemModifierGroupId").value(1));

        verify(menuItemModifierGroupService).getLinkById(1L);
    }

    @Test
    void testGetLinkById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/menu-item-modifier-groups/{id} when not found
        // WHY: Handle missing links
        
        when(menuItemModifierGroupService.getLinkById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/menu-item-modifier-groups/999"))
                .andExpect(status().isNotFound());

        verify(menuItemModifierGroupService).getLinkById(999L);
    }

    @Test
    void testGetModifierGroupsForMenuItem_ShouldReturnGroups() throws Exception {
        // WHAT: Test GET /api/menu-item-modifier-groups/menu-item/{menuItemId}
        // WHY: Get all modifier groups for "Chicken Cutty"
        
        List<MenuItemModifierGroup> links = Arrays.asList(testLink);
        when(menuItemModifierGroupService.getModifierGroupsForMenuItem(1L)).thenReturn(links);

        mockMvc.perform(get("/api/menu-item-modifier-groups/menu-item/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].menuItemId").value(1));

        verify(menuItemModifierGroupService).getModifierGroupsForMenuItem(1L);
    }

    @Test
    void testGetMenuItemsForModifierGroup_ShouldReturnMenuItems() throws Exception {
        // WHAT: Test GET /api/menu-item-modifier-groups/modifier-group/{modifierGroupId}
        // WHY: Get all menu items using "Choose a Side" group
        
        List<MenuItemModifierGroup> links = Arrays.asList(testLink);
        when(menuItemModifierGroupService.getMenuItemsForModifierGroup(1L)).thenReturn(links);

        mockMvc.perform(get("/api/menu-item-modifier-groups/modifier-group/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modifierGroupId").value(1));

        verify(menuItemModifierGroupService).getMenuItemsForModifierGroup(1L);
    }

    @Test
    void testCreateLink_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/menu-item-modifier-groups
        // WHY: Link menu item to modifier group
        
        when(menuItemModifierGroupService.createLink(any(MenuItemModifierGroup.class))).thenReturn(testLink);

        mockMvc.perform(post("/api/menu-item-modifier-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":1,\"modifierGroupId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.menuItemId").value(1));

        verify(menuItemModifierGroupService).createLink(any(MenuItemModifierGroup.class));
    }

    @Test
    void testDeleteLink_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/menu-item-modifier-groups/{id}
        // WHY: Remove modifier group from menu item
        
        doNothing().when(menuItemModifierGroupService).deleteLink(1L);

        mockMvc.perform(delete("/api/menu-item-modifier-groups/1"))
                .andExpect(status().isNoContent());

        verify(menuItemModifierGroupService).deleteLink(1L);
    }
}