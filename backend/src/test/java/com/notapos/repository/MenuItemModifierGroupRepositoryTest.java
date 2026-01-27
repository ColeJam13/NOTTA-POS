package com.notapos.repository;

import com.notapos.entity.MenuItem;
import com.notapos.entity.ModifierGroup;
import com.notapos.entity.MenuItemModifierGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for MenuItemModifierGroupRepository.
 * 
 * Tests database queries for menu item to modifier group relationships using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Creates actual MenuItem and ModifierGroup entities first (proper foreign key handling)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class MenuItemModifierGroupRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private MenuItemModifierGroupRepository menuItemModifierGroupRepository;
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    private MenuItem chickenCutty;
    private MenuItem pancakes;
    private ModifierGroup sidesGroup;
    private ModifierGroup proteinGroup;
    private MenuItemModifierGroup link1;
    private MenuItemModifierGroup link2;
    private MenuItemModifierGroup link3;

    @BeforeEach
    void setUp() {
        // Clear database before each test (in proper order due to foreign keys)
        menuItemModifierGroupRepository.deleteAll();
        menuItemRepository.deleteAll();
        modifierGroupRepository.deleteAll();

        // Create menu items first
        chickenCutty = new MenuItem();
        chickenCutty.setName("Chicken Cutty");
        chickenCutty.setDescription("Buttermilk fried chicken");
        chickenCutty.setPrice(new BigDecimal("17.00"));
        chickenCutty.setCategory("Savory");
        chickenCutty.setPrepStationId(null);
        chickenCutty.setIsActive(true);
        chickenCutty = menuItemRepository.save(chickenCutty);

        pancakes = new MenuItem();
        pancakes.setName("Pancakes");
        pancakes.setDescription("Fluffy buttermilk pancakes");
        pancakes.setPrice(new BigDecimal("12.00"));
        pancakes.setCategory("Sweet");
        pancakes.setPrepStationId(null);
        pancakes.setIsActive(true);
        pancakes = menuItemRepository.save(pancakes);

        // Create modifier groups
        sidesGroup = new ModifierGroup();
        sidesGroup.setName("Choose a Side");
        sidesGroup.setDescription("Select one side dish");
        sidesGroup.setIsRequired(true);
        sidesGroup.setMaxSelections(1);
        sidesGroup.setIsActive(true);
        sidesGroup = modifierGroupRepository.save(sidesGroup);

        proteinGroup = new ModifierGroup();
        proteinGroup.setName("Add Protein");
        proteinGroup.setDescription("Optional protein additions");
        proteinGroup.setIsRequired(false);
        proteinGroup.setMaxSelections(2);
        proteinGroup.setIsActive(true);
        proteinGroup = modifierGroupRepository.save(proteinGroup);

        // Now create junction table links with valid foreign keys
        // Link: Chicken Cutty has Choose a Side
        link1 = new MenuItemModifierGroup();
        link1.setMenuItemId(chickenCutty.getMenuItemId());
        link1.setModifierGroupId(sidesGroup.getModifierGroupId());
        link1 = menuItemModifierGroupRepository.save(link1);

        // Link: Chicken Cutty has Add Protein
        link2 = new MenuItemModifierGroup();
        link2.setMenuItemId(chickenCutty.getMenuItemId());
        link2.setModifierGroupId(proteinGroup.getModifierGroupId());
        link2 = menuItemModifierGroupRepository.save(link2);

        // Link: Pancakes has Choose a Side
        link3 = new MenuItemModifierGroup();
        link3.setMenuItemId(pancakes.getMenuItemId());
        link3.setModifierGroupId(sidesGroup.getModifierGroupId());
        link3 = menuItemModifierGroupRepository.save(link3);
    }

    @Test
    void testSave_ShouldPersistLink() {
        // WHAT: Test saving a new menu item to modifier group link
        // WHY: Ensure basic create operation works
        
        // Given - New link (Pancakes gets Add Protein group)
        MenuItemModifierGroup newLink = new MenuItemModifierGroup();
        newLink.setMenuItemId(pancakes.getMenuItemId());
        newLink.setModifierGroupId(proteinGroup.getModifierGroupId());

        // When - Save to database
        MenuItemModifierGroup saved = menuItemModifierGroupRepository.save(newLink);

        // Then - Should persist with generated ID
        assertNotNull(saved.getMenuItemModifierGroupId());
        assertEquals(pancakes.getMenuItemId(), saved.getMenuItemId());
        assertEquals(proteinGroup.getModifierGroupId(), saved.getModifierGroupId());
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
        assertEquals(chickenCutty.getMenuItemId(), result.get().getMenuItemId());
        assertEquals(sidesGroup.getModifierGroupId(), result.get().getModifierGroupId());
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
        
        // Given - Chicken Cutty has 2 modifier groups (from setUp)
        
        // When - Find modifier groups for Chicken Cutty
        List<MenuItemModifierGroup> itemGroups = menuItemModifierGroupRepository.findByMenuItemId(chickenCutty.getMenuItemId());

        // Then - Should get 2 modifier groups
        assertEquals(2, itemGroups.size());
        assertTrue(itemGroups.stream().allMatch(link -> link.getMenuItemId().equals(chickenCutty.getMenuItemId())));
    }

    @Test
    void testFindByModifierGroupId_ShouldReturnMenuItemsForGroup() {
        // WHAT: Test finding all menu items that use a modifier group
        // WHY: Show which items have "Choose a Side" option
        
        // Given - Choose a Side is used by 2 items (from setUp)
        
        // When - Find menu items with Choose a Side group
        List<MenuItemModifierGroup> groupItems = menuItemModifierGroupRepository.findByModifierGroupId(sidesGroup.getModifierGroupId());

        // Then - Should get 2 menu items
        assertEquals(2, groupItems.size());
        assertTrue(groupItems.stream().allMatch(link -> link.getModifierGroupId().equals(sidesGroup.getModifierGroupId())));
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
        
        // Given - Chicken Cutty has 2 modifier groups (from setUp)
        
        // When - Find all groups for Chicken Cutty
        List<MenuItemModifierGroup> groups = menuItemModifierGroupRepository.findByMenuItemId(chickenCutty.getMenuItemId());

        // Then - Should have 2 different modifier groups
        assertEquals(2, groups.size());
        assertEquals(sidesGroup.getModifierGroupId(), groups.get(0).getModifierGroupId());
        assertEquals(proteinGroup.getModifierGroupId(), groups.get(1).getModifierGroupId());
    }

    @Test
    void testMultipleItemsForOneGroup_ShouldAllowMultipleLinks() {
        // WHAT: Test that one modifier group can apply to multiple menu items
        // WHY: "Choose a Side" applies to both Chicken Cutty and Pancakes
        
        // Given - Choose a Side is used by 2 items (from setUp)
        
        // When - Find all items using Choose a Side
        List<MenuItemModifierGroup> items = menuItemModifierGroupRepository.findByModifierGroupId(sidesGroup.getModifierGroupId());

        // Then - Should have 2 different menu items
        assertEquals(2, items.size());
        assertEquals(chickenCutty.getMenuItemId(), items.get(0).getMenuItemId());
        assertEquals(pancakes.getMenuItemId(), items.get(1).getMenuItemId());
    }

    @Test
    void testJunctionTableRelationship_ShouldMaintainBothDirections() {
        // WHAT: Test that junction table works bidirectionally
        // WHY: Can query from item->groups or group->items
        
        // Given - Links exist in both directions (from setUp)
        
        // When - Query from both directions
        List<MenuItemModifierGroup> itemGroups = menuItemModifierGroupRepository.findByMenuItemId(chickenCutty.getMenuItemId());
        List<MenuItemModifierGroup> groupItems = menuItemModifierGroupRepository.findByModifierGroupId(sidesGroup.getModifierGroupId());

        // Then - Both queries should work
        assertEquals(2, itemGroups.size()); // Chicken Cutty has 2 groups
        assertEquals(2, groupItems.size()); // Choose a Side is on 2 items
    }
}