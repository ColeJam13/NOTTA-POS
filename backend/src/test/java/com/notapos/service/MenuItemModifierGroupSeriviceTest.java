package com.notapos.service;

import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.repository.MenuItemModifierGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MenuItemModifierGroupService.
 * 
 * Tests linking menu items to their modifier groups.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class MenuItemModifierGroupServiceTest {

    @Mock
    private MenuItemModifierGroupRepository menuItemModifierGroupRepository;

    @InjectMocks
    private MenuItemModifierGroupService menuItemModifierGroupService;

    private MenuItemModifierGroup testLink;

    @BeforeEach
    void setUp() {
        // Create a test link (Chicken Cutty has "Choose a Side" modifier group)
        testLink = new MenuItemModifierGroup();
        testLink.setMenuItemModifierGroupId(1L);
        testLink.setMenuItemId(1L);
        testLink.setModifierGroupId(1L);
    }

    @Test
    void testCreateLink_ShouldSaveLink() {
        // WHAT: Test creating a new menu item to modifier group link
        // WHY: Define which customization options apply to which menu items
        
        // Given - Mock returns saved link
        when(menuItemModifierGroupRepository.save(any(MenuItemModifierGroup.class))).thenReturn(testLink);

        // When - Create link
        MenuItemModifierGroup created = menuItemModifierGroupService.createLink(testLink);

        // Then - Should save and return link
        assertNotNull(created);
        assertEquals(1L, created.getMenuItemId());
        assertEquals(1L, created.getModifierGroupId());
        verify(menuItemModifierGroupRepository, times(1)).save(testLink);
    }

    @Test
    void testGetAllLinks_ShouldReturnAllLinks() {
        // WHAT: Test retrieving all menu item to modifier group links
        // WHY: Admin needs to see all configured relationships
        
        // Given - Mock returns 2 links
        MenuItemModifierGroup link2 = new MenuItemModifierGroup();
        link2.setMenuItemId(2L);
        link2.setModifierGroupId(1L);
        List<MenuItemModifierGroup> links = Arrays.asList(testLink, link2);
        when(menuItemModifierGroupRepository.findAll()).thenReturn(links);

        // When - Get all links
        List<MenuItemModifierGroup> result = menuItemModifierGroupService.getAllLinks();

        // Then - Should get both links
        assertEquals(2, result.size());
        verify(menuItemModifierGroupRepository, times(1)).findAll();
    }

    @Test
    void testGetLinkById_WhenExists_ShouldReturnLink() {
        // WHAT: Test finding a specific link by ID
        // WHY: Need to load link details
        
        // Given - Mock returns the link
        when(menuItemModifierGroupRepository.findById(1L)).thenReturn(Optional.of(testLink));

        // When - Get link by ID
        Optional<MenuItemModifierGroup> result = menuItemModifierGroupService.getLinkById(1L);

        // Then - Should find the link
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getMenuItemId());
        verify(menuItemModifierGroupRepository, times(1)).findById(1L);
    }

    @Test
    void testGetModifierGroupsForMenuItem_ShouldReturnItemGroups() {
        // WHAT: Test getting all modifier groups for a menu item
        // WHY: Show which customization options apply (Chicken Cutty has Choose a Side + Add Protein)
        
        // Given - Mock returns modifier groups for menu item
        MenuItemModifierGroup link2 = new MenuItemModifierGroup();
        link2.setMenuItemId(1L);
        link2.setModifierGroupId(2L);
        
        List<MenuItemModifierGroup> itemGroups = Arrays.asList(testLink, link2);
        when(menuItemModifierGroupRepository.findByMenuItemId(1L)).thenReturn(itemGroups);

        // When - Get modifier groups for Chicken Cutty (menu item 1)
        List<MenuItemModifierGroup> result = menuItemModifierGroupService.getModifierGroupsForMenuItem(1L);

        // Then - Should get all modifier groups for that item
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getMenuItemId());
        assertEquals(1L, result.get(1).getMenuItemId());
        verify(menuItemModifierGroupRepository, times(1)).findByMenuItemId(1L);
    }

    @Test
    void testGetMenuItemsForModifierGroup_ShouldReturnGroupItems() {
        // WHAT: Test getting all menu items that use a modifier group
        // WHY: See which items have "Choose a Side" option
        
        // Given - Mock returns menu items for modifier group
        MenuItemModifierGroup link2 = new MenuItemModifierGroup();
        link2.setMenuItemId(2L);
        link2.setModifierGroupId(1L);
        
        List<MenuItemModifierGroup> groupItems = Arrays.asList(testLink, link2);
        when(menuItemModifierGroupRepository.findByModifierGroupId(1L)).thenReturn(groupItems);

        // When - Get menu items with "Choose a Side" group (modifier group 1)
        List<MenuItemModifierGroup> result = menuItemModifierGroupService.getMenuItemsForModifierGroup(1L);

        // Then - Should get all menu items with that modifier group
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getModifierGroupId());
        assertEquals(1L, result.get(1).getModifierGroupId());
        verify(menuItemModifierGroupRepository, times(1)).findByModifierGroupId(1L);
    }

    @Test
    void testDeleteLink_ShouldCallRepository() {
        // WHAT: Test deleting a menu item to modifier group link
        // WHY: Remove customization option from a menu item
        
        // Given - Mock repository
        doNothing().when(menuItemModifierGroupRepository).deleteById(1L);

        // When - Delete link
        menuItemModifierGroupService.deleteLink(1L);

        // Then - Repository delete should be called
        verify(menuItemModifierGroupRepository, times(1)).deleteById(1L);
    }
}