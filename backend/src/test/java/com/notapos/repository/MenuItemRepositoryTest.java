package com.notapos.repository;

import com.notapos.entity.MenuItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for MenuItemRepository.
 * 
 * Tests database queries for menu item management.
 * 
 * @author CJ
 */

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class MenuItemRepositoryTest {

    @Autowired
    private MenuItemRepository menuItemRepository;

    private MenuItem chickenCutty;
    private MenuItem pancakes;
    private MenuItem inactiveItem;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        menuItemRepository.deleteAll();

        // Create active savory item (Chicken Cutty)
        chickenCutty = new MenuItem();
        chickenCutty.setName("Chicken Cutty");
        chickenCutty.setDescription("Buttermilk fried chicken");
        chickenCutty.setPrice(new BigDecimal("17.00"));
        chickenCutty.setCategory("Savory");
        chickenCutty.setPrepStationId(1L); // Kitchen
        chickenCutty.setIsActive(true);
        chickenCutty = menuItemRepository.save(chickenCutty);

        // Create active sweet item (Pancakes)
        pancakes = new MenuItem();
        pancakes.setName("Pancakes");
        pancakes.setDescription("Fluffy buttermilk pancakes");
        pancakes.setPrice(new BigDecimal("12.00"));
        pancakes.setCategory("Sweet");
        pancakes.setPrepStationId(1L); // Kitchen
        pancakes.setIsActive(true);
        pancakes = menuItemRepository.save(pancakes);

        // Create inactive item
        inactiveItem = new MenuItem();
        inactiveItem.setName("Old Burger");
        inactiveItem.setDescription("Discontinued item");
        inactiveItem.setPrice(new BigDecimal("10.00"));
        inactiveItem.setCategory("Savory");
        inactiveItem.setPrepStationId(1L);
        inactiveItem.setIsActive(false); // 86'd
        inactiveItem = menuItemRepository.save(inactiveItem);
    }

    @Test
    void testSave_ShouldPersistMenuItem() {
        // WHAT: Test saving a new menu item to database
        // WHY: Ensure basic create operation works
        
        // Given - New menu item
        MenuItem newItem = new MenuItem();
        newItem.setName("Caesar Salad");
        newItem.setDescription("Fresh romaine with house dressing");
        newItem.setPrice(new BigDecimal("9.00"));
        newItem.setCategory("Savory");
        newItem.setPrepStationId(1L);
        newItem.setIsActive(true);

        // When - Save to database
        MenuItem saved = menuItemRepository.save(newItem);

        // Then - Should persist with generated ID
        assertNotNull(saved.getMenuItemId());
        assertEquals("Caesar Salad", saved.getName());
        assertEquals(new BigDecimal("9.00"), saved.getPrice());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnItem() {
        // WHAT: Test finding menu item by ID
        // WHY: Need to load specific items for orders
        
        // Given - Chicken Cutty exists in database (from setUp)
        
        // When - Find by ID
        Optional<MenuItem> result = menuItemRepository.findById(chickenCutty.getMenuItemId());

        // Then - Should find the item
        assertTrue(result.isPresent());
        assertEquals("Chicken Cutty", result.get().getName());
        assertEquals("Savory", result.get().getCategory());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent menu item
        // WHY: Handle missing items gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<MenuItem> result = menuItemRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllItems() {
        // WHAT: Test retrieving all menu items (active and inactive)
        // WHY: Admin needs to see complete menu including 86'd items
        
        // Given - 3 items in database (from setUp)
        
        // When - Find all
        List<MenuItem> items = menuItemRepository.findAll();

        // Then - Should get all 3 items
        assertEquals(3, items.size());
    }

    @Test
    void testFindByName_ShouldReturnItem() {
        // WHAT: Test finding menu item by exact name
        // WHY: Search functionality for menu management
        
        // Given - Chicken Cutty exists (from setUp)
        
        // When - Find by name
        Optional<MenuItem> result = menuItemRepository.findByName("Chicken Cutty");

        // Then - Should find the item
        assertTrue(result.isPresent());
        assertEquals("Chicken Cutty", result.get().getName());
    }

    @Test
    void testFindByCategory_ShouldReturnItemsInCategory() {
        // WHAT: Test finding all items in a category
        // WHY: Display menu organized by category (Savory, Sweet, etc.)
        
        // Given - 2 Savory items exist (1 active, 1 inactive from setUp)
        
        // When - Find items in Savory category
        List<MenuItem> savoryItems = menuItemRepository.findByCategory("Savory");

        // Then - Should get both Savory items
        assertEquals(2, savoryItems.size());
        assertTrue(savoryItems.stream().allMatch(item -> "Savory".equals(item.getCategory())));
    }

    @Test
    void testFindByCategoryAndIsActive_ShouldReturnActiveItemsInCategory() {
        // WHAT: Test finding only active items in a category
        // WHY: Show customers only available items in Savory category
        
        // Given - 1 active Savory item (Chicken Cutty from setUp)
        
        // When - Find active Savory items
        List<MenuItem> activeSavory = menuItemRepository.findByCategoryAndIsActive("Savory", true);

        // Then - Should get only active Savory items (not the 86'd burger)
        assertEquals(1, activeSavory.size());
        assertEquals("Chicken Cutty", activeSavory.get(0).getName());
        assertTrue(activeSavory.get(0).getIsActive());
    }

    @Test
    void testFindByIsActive_True_ShouldReturnActiveItems() {
        // WHAT: Test finding all active menu items
        // WHY: Display customer-facing menu (only available items)
        
        // Given - 2 active items exist (from setUp)
        
        // When - Find active items
        List<MenuItem> activeItems = menuItemRepository.findByIsActive(true);

        // Then - Should get 2 active items
        assertEquals(2, activeItems.size());
        assertTrue(activeItems.stream().allMatch(MenuItem::getIsActive));
    }

    @Test
    void testFindByIsActive_False_ShouldReturnInactiveItems() {
        // WHAT: Test finding all inactive (86'd) menu items
        // WHY: Show kitchen which items are out of stock
        
        // Given - 1 inactive item exists (from setUp)
        
        // When - Find inactive items
        List<MenuItem> inactiveItems = menuItemRepository.findByIsActive(false);

        // Then - Should get 1 inactive item
        assertEquals(1, inactiveItems.size());
        assertFalse(inactiveItems.get(0).getIsActive());
        assertEquals("Old Burger", inactiveItems.get(0).getName());
    }

    @Test
    void testFindByPrepStationId_ShouldReturnItemsForStation() {
        // WHAT: Test finding items by prep station
        // WHY: Route items to correct station (Kitchen vs Bar)
        
        // Given - All 3 items go to Kitchen (station 1) from setUp
        
        // When - Find items for Kitchen
        List<MenuItem> kitchenItems = menuItemRepository.findByPrepStationId(1L);

        // Then - Should get all 3 items
        assertEquals(3, kitchenItems.size());
        assertTrue(kitchenItems.stream().allMatch(item -> item.getPrepStationId().equals(1L)));
    }

    @Test
    void testDeleteById_ShouldRemoveItem() {
        // WHAT: Test deleting a menu item
        // WHY: Remove test data or duplicate entries
        
        // Given - Inactive item exists
        Long itemId = inactiveItem.getMenuItemId();
        
        // When - Delete the item
        menuItemRepository.deleteById(itemId);

        // Then - Item should no longer exist
        Optional<MenuItem> deleted = menuItemRepository.findById(itemId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingItem() {
        // WHAT: Test updating a menu item's fields
        // WHY: Change prices, descriptions, or availability
        
        // Given - Chicken Cutty exists
        Long itemId = chickenCutty.getMenuItemId();
        
        // When - Update price and description
        chickenCutty.setPrice(new BigDecimal("18.00"));
        chickenCutty.setDescription("Premium fried chicken");
        MenuItem updated = menuItemRepository.save(chickenCutty);

        // Then - Changes should persist
        MenuItem reloaded = menuItemRepository.findById(itemId).orElseThrow();
        assertEquals(new BigDecimal("18.00"), reloaded.getPrice());
        assertEquals("Premium fried chicken", reloaded.getDescription());
    }

    @Test
    void testSoftDelete_ShouldDeactivateItem() {
        // WHAT: Test soft delete (deactivating item instead of removing)
        // WHY: 86 an item without losing historical data
        
        // Given - Chicken Cutty is active
        Long itemId = chickenCutty.getMenuItemId();
        assertTrue(chickenCutty.getIsActive());
        
        // When - Deactivate the item
        chickenCutty.setIsActive(false);
        menuItemRepository.save(chickenCutty);

        // Then - Item should still exist but be inactive
        MenuItem deactivated = menuItemRepository.findById(itemId).orElseThrow();
        assertFalse(deactivated.getIsActive());
    }
}