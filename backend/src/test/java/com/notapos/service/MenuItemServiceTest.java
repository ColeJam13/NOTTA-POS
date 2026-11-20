package com.notapos.service;

import com.notapos.entity.MenuItem;
import com.notapos.repository.MenuItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MenuItemService.
 * 
 * Tests menu item management functionality.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class MenuItemServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @InjectMocks
    private MenuItemService menuItemService;

    private MenuItem testMenuItem;

    @BeforeEach
    void setUp() {
        // Create a test menu item (Chicken Cutty)
        testMenuItem = new MenuItem();
        testMenuItem.setMenuItemId(1L);
        testMenuItem.setName("Chicken Cutty");
        testMenuItem.setDescription("Buttermilk fried chicken");
        testMenuItem.setPrice(new BigDecimal("17.00"));
        testMenuItem.setCategory("Savory");
        testMenuItem.setPrepStationId(1L);
        testMenuItem.setIsActive(true);
    }

    @Test
    void testCreateMenuItem_ShouldSaveMenuItem() {
        // WHAT: Test creating a new menu item
        // WHY: Need to add items to the menu
        
        // Given - Mock returns the saved item
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // When - Create menu item
        MenuItem created = menuItemService.createMenuItem(testMenuItem);

        // Then - Should save and return the item
        assertNotNull(created);
        assertEquals("Chicken Cutty", created.getName());
        assertEquals(new BigDecimal("17.00"), created.getPrice());
        verify(menuItemRepository, times(1)).save(testMenuItem);
    }

    @Test
    void testGetAllMenuItems_ShouldReturnAllItems() {
        // WHAT: Test retrieving all menu items (including inactive)
        // WHY: Admin needs to see full menu for management
        
        // Given - Mock returns 2 items
        MenuItem item2 = new MenuItem();
        item2.setIsActive(false);
        List<MenuItem> items = Arrays.asList(testMenuItem, item2);
        when(menuItemRepository.findAllByOrderByMenuItemIdAsc()).thenReturn(items);

        // When - Get all items
        List<MenuItem> result = menuItemService.getAllMenuItems();

        // Then - Should get both active and inactive items
        assertEquals(2, result.size());
        verify(menuItemRepository, times(1)).findAllByOrderByMenuItemIdAsc();
    }

    @Test
    void testGetActiveMenuItems_ShouldReturnOnlyActiveItems() {
        // WHAT: Test getting only active menu items
        // WHY: Customer-facing menu should only show available items
        
        // Given - Mock returns only active items
        List<MenuItem> activeItems = Arrays.asList(testMenuItem);
        when(menuItemRepository.findByIsActiveOrderByMenuItemIdAsc(true)).thenReturn(activeItems);

        // When - Get active items
        List<MenuItem> result = menuItemService.getActiveMenuItems();

        // Then - Should only get active items
        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());
        verify(menuItemRepository, times(1)).findByIsActiveOrderByMenuItemIdAsc(true);
    }

    @Test
    void testGetMenuItemById_WhenExists_ShouldReturnItem() {
        // WHAT: Test finding a specific menu item by ID
        // WHY: Need to load item details for orders
        
        // Given - Mock returns the item
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));

        // When - Get item by ID
        Optional<MenuItem> result = menuItemService.getMenuItemById(1L);

        // Then - Should find the item
        assertTrue(result.isPresent());
        assertEquals("Chicken Cutty", result.get().getName());
        verify(menuItemRepository, times(1)).findById(1L);
    }

    @Test
    void testGetMenuItemByName_ShouldReturnItem() {
        // WHAT: Test finding menu item by name
        // WHY: Search functionality for servers
        
        // Given - Mock returns item by name
        when(menuItemRepository.findByName("Chicken Cutty")).thenReturn(Optional.of(testMenuItem));

        // When - Search by name
        Optional<MenuItem> result = menuItemService.getMenuItemByName("Chicken Cutty");

        // Then - Should find the item
        assertTrue(result.isPresent());
        assertEquals("Chicken Cutty", result.get().getName());
        verify(menuItemRepository, times(1)).findByName("Chicken Cutty");
    }

    @Test
    void testGetMenuItemsByCategory_ShouldReturnCategoryItems() {
        // WHAT: Test filtering items by category
        // WHY: Menu organized by categories (Savory, Sweet, etc.)
        
        // Given - Mock returns savory items
        List<MenuItem> savoryItems = Arrays.asList(testMenuItem);
        when(menuItemRepository.findByCategory("Savory")).thenReturn(savoryItems);

        // When - Get savory items
        List<MenuItem> result = menuItemService.getMenuItemsByCategory("Savory");

        // Then - Should get items in that category
        assertEquals(1, result.size());
        assertEquals("Savory", result.get(0).getCategory());
        verify(menuItemRepository, times(1)).findByCategory("Savory");
    }

    @Test
    void testGetActiveMenuItemsByCategory_ShouldFilterByBoth() {
        // WHAT: Test getting active items in a specific category
        // WHY: Customer menu shows active items organized by category
        
        // Given - Mock returns active savory items
        List<MenuItem> activeItems = Arrays.asList(testMenuItem);
        when(menuItemRepository.findByCategoryAndIsActive("Savory", true)).thenReturn(activeItems);

        // When - Get active savory items
        List<MenuItem> result = menuItemService.getActiveMenuItemsByCategory("Savory");

        // Then - Should get active items in category
        assertEquals(1, result.size());
        assertEquals("Savory", result.get(0).getCategory());
        assertTrue(result.get(0).getIsActive());
        verify(menuItemRepository, times(1)).findByCategoryAndIsActive("Savory", true);
    }

    @Test
    void testUpdateMenuItem_ShouldUpdateAllFields() {
        // WHAT: Test updating menu item details
        // WHY: Prices change, descriptions get updated
        
        // Given - Item exists
        MenuItem updatedData = new MenuItem();
        updatedData.setName("Updated Chicken Cutty");
        updatedData.setDescription("New description");
        updatedData.setPrice(new BigDecimal("18.00"));
        updatedData.setCategory("Savory");
        updatedData.setPrepStationId(1L);
        updatedData.setIsActive(true);

        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // When - Update the item
        MenuItem result = menuItemService.updateMenuItem(1L, updatedData);

        // Then - All fields should be updated
        assertEquals("Updated Chicken Cutty", result.getName());
        assertEquals(new BigDecimal("18.00"), result.getPrice());
        verify(menuItemRepository, times(1)).save(testMenuItem);
    }

    @Test
    void testUpdateMenuItem_WhenNotFound_ShouldThrowException() {
        // WHAT: Test error handling when item doesn't exist
        // WHY: Can't update non-existent items
        
        // Given - Item doesn't exist
        when(menuItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then - Should throw exception
        assertThrows(RuntimeException.class, () -> {
            menuItemService.updateMenuItem(999L, new MenuItem());
        });
    }

    @Test
    void testDeactivateMenuItem_ShouldSetInactive() {
        // WHAT: Test soft delete (marking item inactive)
        // WHY: Don't delete items - preserves order history
        
        // Given - Active item exists
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // When - Deactivate the item
        MenuItem result = menuItemService.deactivateMenuItem(1L);

        // Then - Item should be inactive
        assertFalse(result.getIsActive());
        verify(menuItemRepository, times(1)).save(testMenuItem);
    }

    @Test
    void testActivateMenuItem_ShouldSetActive() {
        // WHAT: Test reactivating an inactive item
        // WHY: Seasonal items come back on menu
        
        // Given - Inactive item exists
        testMenuItem.setIsActive(false);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // When - Activate the item
        MenuItem result = menuItemService.activateMenuItem(1L);

        // Then - Item should be active
        assertTrue(result.getIsActive());
        verify(menuItemRepository, times(1)).save(testMenuItem);
    }

    @Test
    void testGetMenuItemsByPrepStation_ShouldFilterByStation() {
        // WHAT: Test getting items for specific prep station
        // WHY: Kitchen vs Bar items go to different stations
        
        // Given - Mock returns kitchen items
        List<MenuItem> kitchenItems = Arrays.asList(testMenuItem);
        when(menuItemRepository.findByPrepStationId(1L)).thenReturn(kitchenItems);

        // When - Get items for kitchen (station 1)
        List<MenuItem> result = menuItemService.getMenuItemsByPrepStation(1L);

        // Then - Should get items for that station
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getPrepStationId());
        verify(menuItemRepository, times(1)).findByPrepStationId(1L);
    }

    @Test
    void testDeleteMenuItem_ShouldCallRepository() {
        // WHAT: Test hard delete (actually remove from database)
        // WHY: Remove test data or duplicates (not recommended for production)
        
        // Given - Mock repository
        doNothing().when(menuItemRepository).deleteById(1L);

        // When - Delete item
        menuItemService.deleteMenuItem(1L);

        // Then - Repository delete should be called
        verify(menuItemRepository, times(1)).deleteById(1L);
    }

    @Test
    void testSet86d_True_ShouldMarkItemAs86d() {
        // WHAT: Test 86'ing an item
        // WHY: The kitchen ran out. The item stays on the menu (still active) but cannot be ordered.

        // Given - An active item that is not 86'd
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // When
        MenuItem result = menuItemService.set86d(1L, true);

        // Then - 86'd, and still on the menu
        assertTrue(result.getIs86d());
        assertTrue(result.getIsActive());
        verify(menuItemRepository, times(1)).save(testMenuItem);
    }

    @Test
    void testSet86d_False_ShouldPutItemBackOn() {
        // WHAT: Test un-86'ing an item
        // WHY: The kitchen has it again

        // Given - An 86'd item
        testMenuItem.setIs86d(true);
        when(menuItemRepository.findById(1L)).thenReturn(Optional.of(testMenuItem));
        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(testMenuItem);

        // When
        MenuItem result = menuItemService.set86d(1L, false);

        // Then
        assertFalse(result.getIs86d());
    }

    @Test
    void testSet86d_WhenNotFound_ShouldThrowException() {
        // WHAT: Test 86'ing an item that does not exist
        // WHY: Becomes a 404 for the screen

        // Given
        when(menuItemRepository.findById(99L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(com.notapos.exception.ResourceNotFoundException.class, () -> menuItemService.set86d(99L, true));
        verify(menuItemRepository, never()).save(any());
    }

    @Test
    void testGet86dMenuItems_ShouldAskForActiveItemsThatAre86d() {
        // WHAT: Test listing what is 86'd right now
        // WHY: The 86 board shows this list. Items taken off the menu entirely are not on it.

        // Given
        testMenuItem.setIs86d(true);
        when(menuItemRepository.findByIs86dAndIsActiveOrderByMenuItemIdAsc(true, true)).thenReturn(List.of(testMenuItem));

        // When
        List<MenuItem> result = menuItemService.get86dMenuItems();

        // Then
        assertEquals(1, result.size());
        verify(menuItemRepository, times(1)).findByIs86dAndIsActiveOrderByMenuItemIdAsc(true, true);
    }

    @Test
    void testNewMenuItem_ShouldNotBe86d() {
        // WHAT: Test the starting value of the 86 flag
        // WHY: A new item is available until someone says otherwise

        assertFalse(new MenuItem().getIs86d());
    }
}
