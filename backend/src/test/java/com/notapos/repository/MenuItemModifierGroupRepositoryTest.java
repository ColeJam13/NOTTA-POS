package com.notapos.repository;

import com.notapos.entity.MenuItemModifierGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for MenuItemModifierGroupRepository.
 * 
 * Tests database queries for menu item to modifier group relationships.
 * 
 * @author CJ
 */

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ActiveProfiles("test")
class MenuItemModifierGroupRepositoryTest {

    @Autowired
    private MenuItemModifierGroupRepository menuItemModifierGroupRepository;

    private MenuItemModifierGroup link1;
    private MenuItemModifierGroup link2;
    private MenuItemModifierGroup link3;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        menuItemModifierGroupRepository.deleteAll();

        // Create link: Chicken Cutty (item 1) has Choose a Side (group 1)
        link1 = new MenuItemModifierGroup();
        link1.setMenuItemId(1L);
        link1.setModifierGroupId(1L);
        link1 = menuItemModifierGroupRepository.save(link1);

        // Create link: Chicken Cutty (item 1) has Add Protein (group 2)
        link2 = new MenuItemModifierGroup();
        link2.setMenuItemId(1L);
        link2.setModifierGroupId(2L);
        link2 = menuItemModifierGroupRepository.save(link2);

        // Create link: Pancakes (item 2) has Choose a Side (group 1)
        link3 = new MenuItemModifierGroup();
        link3.setMenuItemId(2L);
        link3.setModifierGroupId(1L);
        link3 = menuItemModifierGroupRepository.save(link3);
    }

    @Test
    void testSave_ShouldPersistLink() {
        // WHAT: Test saving a new menu item to modifier group link
        // WHY: Ensure basic create operation works
        
        // Given - New link
        MenuItemModifierGroup newLink = new MenuItemModifierGroup();
        newLink.setMenuItemId(3L);
        newLink.setModifierGroupId(2L);

        // When - Save to database
        MenuItemModifierGroup saved = menuItemModifierGroupRepository.save(newLink);

        // Then - Should persist with generated ID
        assertNotNull(saved.getMenuItemModifierGroupId());
        assertEquals(3L, saved.getMenuItemId());
        assertEquals(2L, saved.getModifierGroupId());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnLink() {
        // WHAT: Test finding link by ID
        // WHY: Need to load specific relationships
        
        // Given - Link exists in database (from setUp)
        
        // When - Find by ID
        Optional<MenuItemModifierGroup> result = menuItemModifierGroupRepository.findById(link1.getMenuItemModifierGroupId());

        // Then - Should find the link
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getMenuItemId());
        assertEquals(1L, result.get().getModifierGroupId());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent link
        // WHY: Handle missing links gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<MenuItemModifierGroup> result = menuItemModifierGroupRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllLinks() {
        // WHAT: Test retrieving all links
        // WHY: Get complete configuration of menu item customizations
        
        // Given - 3 links in database (from setUp)
        
        // When - Find all
        List<MenuItemModifierGroup> links = menuItemModifierGroupRepository.findAll();

        // Then - Should get all 3 links
        assertEquals(3, links.size());
    }

    @Test
    void testFindByMenuItemId_ShouldReturnModifierGroupsForItem() {
        // WHAT: Test finding all modifier groups for a menu item
        // WHY: Show which customizations apply to Chicken Cutty
        
        // Given - Menu item 1 (Chicken Cutty) has 2 modifier groups (from setUp)
        
        // When - Find modifier groups for menu item 1
        List<MenuItemModifierGroup> itemGroups = menuItemModifierGroupRepository.findByMenuItemId(1L);

        // Then - Should get 2 modifier groups
        assertEquals(2, itemGroups.size());
        assertTrue(itemGroups.stream().allMatch(link -> link.getMenuItemId().equals(1L)));
    }

    @Test
    void testFindByModifierGroupId_ShouldReturnMenuItemsForGroup() {
        // WHAT: Test finding all menu items that use a modifier group
        // WHY: Show which items have "Choose a Side" option
        
        // Given - Modifier group 1 (Choose a Side) is used by 2 items (from setUp)
        
        // When - Find menu items with modifier group 1
        List<MenuItemModifierGroup> groupItems = menuItemModifierGroupRepository.findByModifierGroupId(1L);

        // Then - Should get 2 menu items
        assertEquals(2, groupItems.size());
        assertTrue(groupItems.stream().allMatch(link -> link.getModifierGroupId().equals(1L)));
    }

    @Test
    void testDeleteById_ShouldRemoveLink() {
        // WHAT: Test deleting a link
        // WHY: Remove customization option from a menu item
        
        // Given - Link exists
        Long linkId = link3.getMenuItemModifierGroupId();
        
        // When - Delete the link
        menuItemModifierGroupRepository.deleteById(linkId);

        // Then - Link should no longer exist
        Optional<MenuItemModifierGroup> deleted = menuItemModifierGroupRepository.findById(linkId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testMultipleGroupsForOneItem_ShouldAllowMultipleLinks() {
        // WHAT: Test that one menu item can have multiple modifier groups
        // WHY: Chicken Cutty has both "Choose a Side" and "Add Protein"
        
        // Given - Menu item 1 has 2 modifier groups (from setUp)
        
        // When - Find all groups for menu item 1
        List<MenuItemModifierGroup> groups = menuItemModifierGroupRepository.findByMenuItemId(1L);

        // Then - Should have 2 different modifier groups
        assertEquals(2, groups.size());
        assertEquals(1L, groups.get(0).getModifierGroupId());
        assertEquals(2L, groups.get(1).getModifierGroupId());
    }

    @Test
    void testMultipleItemsForOneGroup_ShouldAllowMultipleLinks() {
        // WHAT: Test that one modifier group can apply to multiple menu items
        // WHY: "Choose a Side" applies to both Chicken Cutty and Pancakes
        
        // Given - Modifier group 1 is used by 2 items (from setUp)
        
        // When - Find all items using group 1
        List<MenuItemModifierGroup> items = menuItemModifierGroupRepository.findByModifierGroupId(1L);

        // Then - Should have 2 different menu items
        assertEquals(2, items.size());
        assertEquals(1L, items.get(0).getMenuItemId());
        assertEquals(2L, items.get(1).getMenuItemId());
    }

    @Test
    void testJunctionTableRelationship_ShouldMaintainBothDirections() {
        // WHAT: Test that junction table works bidirectionally
        // WHY: Can query from item->groups or group->items
        
        // Given - Links exist in both directions (from setUp)
        
        // When - Query from both directions
        List<MenuItemModifierGroup> itemGroups = menuItemModifierGroupRepository.findByMenuItemId(1L);
        List<MenuItemModifierGroup> groupItems = menuItemModifierGroupRepository.findByModifierGroupId(1L);

        // Then - Both queries should work
        assertEquals(2, itemGroups.size()); // Item 1 has 2 groups
        assertEquals(2, groupItems.size()); // Group 1 is on 2 items
    }
}