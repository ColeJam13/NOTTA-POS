package com.notapos.repository;

import com.notapos.entity.Modifier;
import com.notapos.entity.ModifierGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for ModifierRepository.
 * 
 * Tests database queries for modifier management using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Creates actual ModifierGroup entities first (proper foreign key handling)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class ModifierRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private ModifierRepository modifierRepository;
    
    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    private ModifierGroup sidesGroup;
    private ModifierGroup proteinGroup;
    private Modifier fries;
    private Modifier salad;
    private Modifier addBacon;
    private Modifier inactiveModifier;

    @BeforeEach
    void setUp() {
        // Clear database before each test (in proper order due to foreign keys)
        modifierRepository.deleteAll();
        modifierGroupRepository.deleteAll();

        // Create modifier groups first (foreign key dependency)
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

        // Now create modifiers with valid foreign keys
        // Create active modifier with no price adjustment (Fries)
        fries = new Modifier();
        fries.setModifierGroupId(sidesGroup.getModifierGroupId());
        fries.setName("Fries");
        fries.setPriceAdjustment(BigDecimal.ZERO);
        fries.setIsActive(true);
        fries = modifierRepository.save(fries);

        // Create active modifier with no price adjustment (Salad)
        salad = new Modifier();
        salad.setModifierGroupId(sidesGroup.getModifierGroupId());
        salad.setName("Salad");
        salad.setPriceAdjustment(BigDecimal.ZERO);
        salad.setIsActive(true);
        salad = modifierRepository.save(salad);

        // Create active modifier with price adjustment (Add Bacon)
        addBacon = new Modifier();
        addBacon.setModifierGroupId(proteinGroup.getModifierGroupId());
        addBacon.setName("Add Bacon");
        addBacon.setPriceAdjustment(new BigDecimal("2.00"));
        addBacon.setIsActive(true);
        addBacon = modifierRepository.save(addBacon);

        // Create inactive modifier
        inactiveModifier = new Modifier();
        inactiveModifier.setModifierGroupId(sidesGroup.getModifierGroupId());
        inactiveModifier.setName("Old Option");
        inactiveModifier.setPriceAdjustment(BigDecimal.ZERO);
        inactiveModifier.setIsActive(false);
        inactiveModifier = modifierRepository.save(inactiveModifier);
    }

    @Test
    void testSave_ShouldPersistModifier() {
        // WHAT: Test saving a new modifier to database
        // WHY: Ensure basic create operation works
        
        // Given - New modifier
        Modifier newModifier = new Modifier();
        newModifier.setModifierGroupId(proteinGroup.getModifierGroupId());
        newModifier.setName("Add Avocado");
        newModifier.setPriceAdjustment(new BigDecimal("1.50"));
        newModifier.setIsActive(true);

        // When - Save to database
        Modifier saved = modifierRepository.save(newModifier);

        // Then - Should persist with generated ID
        assertNotNull(saved.getModifierId());
        assertEquals("Add Avocado", saved.getName());
        assertEquals(new BigDecimal("1.50"), saved.getPriceAdjustment());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnModifier() {
        // WHAT: Test finding modifier by ID
        // WHY: Need to load specific modifiers for order processing
        
        // Given - Fries modifier exists in database (from setUp)
        
        // When - Find by ID
        Optional<Modifier> result = modifierRepository.findById(fries.getModifierId());

        // Then - Should find the modifier
        assertTrue(result.isPresent());
        assertEquals("Fries", result.get().getName());
        assertEquals(BigDecimal.ZERO, result.get().getPriceAdjustment());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent modifier
        // WHY: Handle missing modifiers gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<Modifier> result = modifierRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllModifiers() {
        // WHAT: Test retrieving all modifiers
        // WHY: Get complete list for admin configuration
        
        // Given - 4 modifiers in database (from setUp)
        
        // When - Find all
        List<Modifier> modifiers = modifierRepository.findAll();

        // Then - Should get all 4 modifiers
        assertEquals(4, modifiers.size());
    }

    @Test
    void testFindByModifierGroupId_ShouldReturnModifiersInGroup() {
        // WHAT: Test finding all modifiers in a modifier group
        // WHY: Show all options for "Choose a Side" group
        
        // Given - sidesGroup has 3 modifiers (Fries, Salad, Old Option from setUp)
        
        // When - Find modifiers for sidesGroup
        List<Modifier> group1Modifiers = modifierRepository.findByModifierGroupId(sidesGroup.getModifierGroupId());

        // Then - Should get 3 modifiers
        assertEquals(3, group1Modifiers.size());
        assertTrue(group1Modifiers.stream().allMatch(m -> m.getModifierGroupId().equals(sidesGroup.getModifierGroupId())));
    }

    @Test
    void testFindByModifierGroupIdAndIsActive_ShouldReturnActiveModifiersInGroup() {
        // WHAT: Test finding only active modifiers in a group
        // WHY: Show available options for "Choose a Side" (not 86'd items)
        
        // Given - sidesGroup has 2 active modifiers (Fries, Salad from setUp)
        
        // When - Find active modifiers for sidesGroup
        List<Modifier> activeGroup1 = modifierRepository.findByModifierGroupIdAndIsActive(sidesGroup.getModifierGroupId(), true);

        // Then - Should get 2 active modifiers
        assertEquals(2, activeGroup1.size());
        assertTrue(activeGroup1.stream().allMatch(Modifier::getIsActive));
        assertTrue(activeGroup1.stream().anyMatch(m -> "Fries".equals(m.getName())));
        assertTrue(activeGroup1.stream().anyMatch(m -> "Salad".equals(m.getName())));
    }

    @Test
    void testFindByIsActive_True_ShouldReturnActiveModifiers() {
        // WHAT: Test finding all active modifiers
        // WHY: Show only available customization options
        
        // Given - 3 active modifiers exist (from setUp)
        
        // When - Find active modifiers
        List<Modifier> active = modifierRepository.findByIsActive(true);

        // Then - Should get 3 active modifiers
        assertEquals(3, active.size());
        assertTrue(active.stream().allMatch(Modifier::getIsActive));
    }

    @Test
    void testFindByIsActive_False_ShouldReturnInactiveModifiers() {
        // WHAT: Test finding all inactive modifiers
        // WHY: Show discontinued options for records
        
        // Given - 1 inactive modifier exists (from setUp)
        
        // When - Find inactive modifiers
        List<Modifier> inactive = modifierRepository.findByIsActive(false);

        // Then - Should get 1 inactive modifier
        assertEquals(1, inactive.size());
        assertFalse(inactive.get(0).getIsActive());
        assertEquals("Old Option", inactive.get(0).getName());
    }

    @Test
    void testDeleteById_ShouldRemoveModifier() {
        // WHAT: Test deleting a modifier
        // WHY: Remove unused options from system
        
        // Given - Inactive modifier exists
        Long modifierId = inactiveModifier.getModifierId();
        
        // When - Delete the modifier
        modifierRepository.deleteById(modifierId);

        // Then - Modifier should no longer exist
        Optional<Modifier> deleted = modifierRepository.findById(modifierId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingModifier() {
        // WHAT: Test updating a modifier's fields
        // WHY: Change prices or availability
        
        // Given - Add Bacon exists
        Long modifierId = addBacon.getModifierId();
        
        // When - Update price adjustment and name
        addBacon.setPriceAdjustment(new BigDecimal("2.50"));
        addBacon.setName("Add Premium Bacon");
        Modifier updated = modifierRepository.save(addBacon);

        // Then - Changes should persist
        Modifier reloaded = modifierRepository.findById(modifierId).orElseThrow();
        assertEquals(new BigDecimal("2.50"), reloaded.getPriceAdjustment());
        assertEquals("Add Premium Bacon", reloaded.getName());
    }

    @Test
    void testModifierWithPriceAdjustment_ShouldStorePricing() {
        // WHAT: Test that price adjustments are stored correctly
        // WHY: Track upcharge modifiers (Add Bacon +$2.00)
        
        // Given - Add Bacon has price adjustment (from setUp)
        
        // When - Load Add Bacon
        Modifier loaded = modifierRepository.findById(addBacon.getModifierId()).orElseThrow();

        // Then - Price adjustment should be preserved
        assertEquals(new BigDecimal("2.00"), loaded.getPriceAdjustment());
        assertEquals("Add Bacon", loaded.getName());
    }
}