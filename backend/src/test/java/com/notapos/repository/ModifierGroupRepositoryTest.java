package com.notapos.repository;

import com.notapos.entity.ModifierGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for ModifierGroupRepository.
 * 
 * Tests database queries for modifier group management using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class ModifierGroupRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    private ModifierGroup chooseASide;
    private ModifierGroup addProtein;
    private ModifierGroup inactiveGroup;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        modifierGroupRepository.deleteAll();

        // Create active required modifier group (Choose a Side)
        chooseASide = new ModifierGroup();
        chooseASide.setName("Choose a Side");
        chooseASide.setDescription("Select one side dish");
        chooseASide.setIsRequired(true);
        chooseASide.setMaxSelections(1);
        chooseASide.setIsActive(true);
        chooseASide = modifierGroupRepository.save(chooseASide);

        // Create active optional modifier group (Add Protein)
        addProtein = new ModifierGroup();
        addProtein.setName("Add Protein");
        addProtein.setDescription("Optional protein additions");
        addProtein.setIsRequired(false);
        addProtein.setMaxSelections(2);
        addProtein.setIsActive(true);
        addProtein = modifierGroupRepository.save(addProtein);

        // Create inactive modifier group
        inactiveGroup = new ModifierGroup();
        inactiveGroup.setName("Old Toppings");
        inactiveGroup.setDescription("Discontinued options");
        inactiveGroup.setIsRequired(false);
        inactiveGroup.setMaxSelections(3);
        inactiveGroup.setIsActive(false);
        inactiveGroup = modifierGroupRepository.save(inactiveGroup);
    }

    @Test
    void testSave_ShouldPersistModifierGroup() {
        // WHAT: Test saving a new modifier group to database
        // WHY: Ensure basic create operation works
        
        // Given - New modifier group
        ModifierGroup newGroup = new ModifierGroup();
        newGroup.setName("Choose Dressing");
        newGroup.setDescription("Salad dressing options");
        newGroup.setIsRequired(true);
        newGroup.setMaxSelections(1);
        newGroup.setIsActive(true);

        // When - Save to database
        ModifierGroup saved = modifierGroupRepository.save(newGroup);

        // Then - Should persist with generated ID
        assertNotNull(saved.getModifierGroupId());
        assertEquals("Choose Dressing", saved.getName());
        assertTrue(saved.getIsRequired());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnGroup() {
        // WHAT: Test finding modifier group by ID
        // WHY: Need to load specific groups for menu configuration
        
        // Given - Choose a Side exists in database (from setUp)
        
        // When - Find by ID
        Optional<ModifierGroup> result = modifierGroupRepository.findById(chooseASide.getModifierGroupId());

        // Then - Should find the group
        assertTrue(result.isPresent());
        assertEquals("Choose a Side", result.get().getName());
        assertTrue(result.get().getIsRequired());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent modifier group
        // WHY: Handle missing groups gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<ModifierGroup> result = modifierGroupRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllGroups() {
        // WHAT: Test retrieving all modifier groups
        // WHY: Get complete list for admin configuration
        
        // Given - 3 groups in database (from setUp)
        
        // When - Find all
        List<ModifierGroup> groups = modifierGroupRepository.findAll();

        // Then - Should get all 3 groups
        assertEquals(3, groups.size());
    }

    @Test
    void testFindByName_ShouldReturnGroup() {
        // WHAT: Test finding modifier group by name
        // WHY: Look up groups by their display names
        
        // Given - Choose a Side exists (from setUp)
        
        // When - Find by name
        Optional<ModifierGroup> result = modifierGroupRepository.findByName("Choose a Side");

        // Then - Should find the group
        assertTrue(result.isPresent());
        assertEquals("Choose a Side", result.get().getName());
        assertEquals(1, result.get().getMaxSelections());
    }

    @Test
    void testFindByIsActive_True_ShouldReturnActiveGroups() {
        // WHAT: Test finding all active modifier groups
        // WHY: Show only available customization options
        
        // Given - 2 active groups exist (from setUp)
        
        // When - Find active groups
        List<ModifierGroup> active = modifierGroupRepository.findByIsActive(true);

        // Then - Should get 2 active groups
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(ModifierGroup::getIsActive));
    }

    @Test
    void testFindByIsActive_False_ShouldReturnInactiveGroups() {
        // WHAT: Test finding all inactive modifier groups
        // WHY: Show discontinued customization options
        
        // Given - 1 inactive group exists (from setUp)
        
        // When - Find inactive groups
        List<ModifierGroup> inactive = modifierGroupRepository.findByIsActive(false);

        // Then - Should get 1 inactive group
        assertEquals(1, inactive.size());
        assertFalse(inactive.get(0).getIsActive());
        assertEquals("Old Toppings", inactive.get(0).getName());
    }

    @Test
    void testFindByIsRequired_True_ShouldReturnRequiredGroups() {
        // WHAT: Test finding required modifier groups
        // WHY: Enforce that customers must make these selections
        
        // Given - 1 required group exists (Choose a Side from setUp)
        
        // When - Find required groups
        List<ModifierGroup> required = modifierGroupRepository.findByIsRequired(true);

        // Then - Should get 1 required group
        assertEquals(1, required.size());
        assertTrue(required.get(0).getIsRequired());
        assertEquals("Choose a Side", required.get(0).getName());
    }

    @Test
    void testFindByIsRequired_False_ShouldReturnOptionalGroups() {
        // WHAT: Test finding optional modifier groups
        // WHY: Show optional customizations (Add Protein, etc.)
        
        // Given - 2 optional groups exist (from setUp)
        
        // When - Find optional groups
        List<ModifierGroup> optional = modifierGroupRepository.findByIsRequired(false);

        // Then - Should get 2 optional groups
        assertEquals(2, optional.size());
        assertTrue(optional.stream().noneMatch(ModifierGroup::getIsRequired));
    }

    @Test
    void testDeleteById_ShouldRemoveGroup() {
        // WHAT: Test deleting a modifier group
        // WHY: Remove unused customization groups
        
        // Given - Inactive group exists
        Long groupId = inactiveGroup.getModifierGroupId();
        
        // When - Delete the group
        modifierGroupRepository.deleteById(groupId);

        // Then - Group should no longer exist
        Optional<ModifierGroup> deleted = modifierGroupRepository.findById(groupId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingGroup() {
        // WHAT: Test updating a modifier group's fields
        // WHY: Change group settings or availability
        
        // Given - Choose a Side exists
        Long groupId = chooseASide.getModifierGroupId();
        
        // When - Update description and max selections
        chooseASide.setDescription("Pick your favorite side");
        chooseASide.setMaxSelections(2);
        ModifierGroup updated = modifierGroupRepository.save(chooseASide);

        // Then - Changes should persist
        ModifierGroup reloaded = modifierGroupRepository.findById(groupId).orElseThrow();
        assertEquals("Pick your favorite side", reloaded.getDescription());
        assertEquals(2, reloaded.getMaxSelections());
    }
}