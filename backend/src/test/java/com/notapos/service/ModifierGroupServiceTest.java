package com.notapos.service;

import com.notapos.entity.ModifierGroup;
import com.notapos.repository.ModifierGroupRepository;
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
 * Unit tests for ModifierGroupService.
 * 
 * Tests modifier group management (Choose a Side, Add Protein, etc.).
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class ModifierGroupServiceTest {

    @Mock
    private ModifierGroupRepository modifierGroupRepository;

    @InjectMocks
    private ModifierGroupService modifierGroupService;

    private ModifierGroup testGroup;

    @BeforeEach
    void setUp() {
        // Create a test modifier group (Choose a Side)
        testGroup = new ModifierGroup();
        testGroup.setModifierGroupId(1L);
        testGroup.setName("Choose a Side");
        testGroup.setDescription("Pick one side dish");
        testGroup.setIsRequired(true);
        testGroup.setMaxSelections(1);
        testGroup.setIsActive(true);
    }

    @Test
    void testCreateModifierGroup_ShouldSaveGroup() {
        // WHAT: Test creating a new modifier group
        // WHY: Need to set up customization options for menu items
        
        // Given - Mock returns saved group
        when(modifierGroupRepository.save(any(ModifierGroup.class))).thenReturn(testGroup);

        // When - Create modifier group
        ModifierGroup created = modifierGroupService.createModifierGroup(testGroup);

        // Then - Should save and return group
        assertNotNull(created);
        assertEquals("Choose a Side", created.getName());
        assertTrue(created.getIsRequired());
        assertEquals(1, created.getMaxSelections());
        verify(modifierGroupRepository, times(1)).save(testGroup);
    }

    @Test
    void testGetAllModifierGroups_ShouldReturnAllGroups() {
        // WHAT: Test retrieving all modifier groups (active and inactive)
        // WHY: Admin needs to see all configured groups
        
        // Given - Mock returns 2 groups
        ModifierGroup group2 = new ModifierGroup();
        group2.setName("Add Protein");
        List<ModifierGroup> groups = Arrays.asList(testGroup, group2);
        when(modifierGroupRepository.findAll()).thenReturn(groups);

        // When - Get all groups
        List<ModifierGroup> result = modifierGroupService.getAllModifierGroups();

        // Then - Should get both groups
        assertEquals(2, result.size());
        verify(modifierGroupRepository, times(1)).findAll();
    }

    @Test
    void testGetActiveModifierGroups_ShouldReturnOnlyActive() {
        // WHAT: Test getting only active modifier groups
        // WHY: Only show active customization options to servers
        
        // Given - Mock returns only active groups
        List<ModifierGroup> activeGroups = Arrays.asList(testGroup);
        when(modifierGroupRepository.findByIsActive(true)).thenReturn(activeGroups);

        // When - Get active groups
        List<ModifierGroup> result = modifierGroupService.getActiveModifierGroups();

        // Then - Should get only active groups
        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());
        verify(modifierGroupRepository, times(1)).findByIsActive(true);
    }

    @Test
    void testGetModifierGroupById_WhenExists_ShouldReturnGroup() {
        // WHAT: Test finding a specific modifier group by ID
        // WHY: Need to load group details
        
        // Given - Mock returns the group
        when(modifierGroupRepository.findById(1L)).thenReturn(Optional.of(testGroup));

        // When - Get group by ID
        Optional<ModifierGroup> result = modifierGroupService.getModifierGroupById(1L);

        // Then - Should find the group
        assertTrue(result.isPresent());
        assertEquals("Choose a Side", result.get().getName());
        verify(modifierGroupRepository, times(1)).findById(1L);
    }

    @Test
    void testGetModifierGroupByName_ShouldReturnGroup() {
        // WHAT: Test finding modifier group by name
        // WHY: Look up group by name
        
        // Given - Mock returns group by name
        when(modifierGroupRepository.findByName("Choose a Side")).thenReturn(Optional.of(testGroup));

        // When - Find group by name
        Optional<ModifierGroup> result = modifierGroupService.getModifierGroupByName("Choose a Side");

        // Then - Should find the group
        assertTrue(result.isPresent());
        assertEquals("Choose a Side", result.get().getName());
        verify(modifierGroupRepository, times(1)).findByName("Choose a Side");
    }

    @Test
    void testUpdateModifierGroup_ShouldUpdateFields() {
        // WHAT: Test updating modifier group details
        // WHY: Change group name, requirements, or selection limits
        
        // Given - Group exists
        ModifierGroup updatedData = new ModifierGroup();
        updatedData.setName("Updated Side Options");
        updatedData.setDescription("New description");
        updatedData.setIsRequired(false);
        updatedData.setMaxSelections(2);
        updatedData.setIsActive(true);

        when(modifierGroupRepository.findById(1L)).thenReturn(Optional.of(testGroup));
        when(modifierGroupRepository.save(any(ModifierGroup.class))).thenReturn(testGroup);

        // When - Update group
        ModifierGroup result = modifierGroupService.updateModifierGroup(1L, updatedData);

        // Then - Should update all fields
        assertEquals("Updated Side Options", result.getName());
        assertEquals("New description", result.getDescription());
        assertFalse(result.getIsRequired());
        assertEquals(2, result.getMaxSelections());
        verify(modifierGroupRepository, times(1)).save(testGroup);
    }

    @Test
    void testUpdateModifierGroup_WhenNotFound_ShouldThrowException() {
        // WHAT: Test error handling when group doesn't exist
        // WHY: Can't update non-existent group
        
        // Given - Group doesn't exist
        when(modifierGroupRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then - Should throw exception
        assertThrows(RuntimeException.class, () -> {
            modifierGroupService.updateModifierGroup(999L, new ModifierGroup());
        });
    }

    @Test
    void testDeleteModifierGroup_ShouldCallRepository() {
        // WHAT: Test deleting a modifier group
        // WHY: Remove unused customization groups
        
        // Given - Mock repository
        doNothing().when(modifierGroupRepository).deleteById(1L);

        // When - Delete group
        modifierGroupService.deleteModifierGroup(1L);

        // Then - Repository delete should be called
        verify(modifierGroupRepository, times(1)).deleteById(1L);
    }
}