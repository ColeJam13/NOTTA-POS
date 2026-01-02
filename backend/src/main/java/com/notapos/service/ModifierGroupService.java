package com.notapos.service;

import com.notapos.entity.ModifierGroup;
import com.notapos.repository.ModifierGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for ModifierGroup operations.
 * 
 * @author CJ
 */

@Service
public class ModifierGroupService {
    
    private final ModifierGroupRepository modifierGroupRepository;

    @Autowired
    public ModifierGroupService(ModifierGroupRepository modifierGroupRepository) {
        this.modifierGroupRepository = modifierGroupRepository;
    }

    public List<ModifierGroup> getAllModifierGroups() {                         // Get all ModifierGroups
        return modifierGroupRepository.findAll();
    }

    public List<ModifierGroup> getActiveModifierGroups() {                      // Get all active ModifierGroups
        return modifierGroupRepository.findByIsActive(true);
    }

    public Optional<ModifierGroup> getModifierGroupById(Long id) {              // Get ModifierGroup by ID
        return modifierGroupRepository.findById(id);
    }

    public Optional<ModifierGroup> getModifierGroupByName(String name) {        // Get ModifierGroup by name
        return modifierGroupRepository.findByName(name);
    }

    public ModifierGroup createModifierGroup(ModifierGroup modifierGroup) {     // Create new ModififerGroup
        return modifierGroupRepository.save(modifierGroup);
    }

    public ModifierGroup updateModifierGroup(Long id, ModifierGroup updatedGroup) {             // Update existing ModififerGroup
        ModifierGroup existing = modifierGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modifier group not found with id: " +id));

                existing.setName(updatedGroup.getName());
                existing.setDescription(updatedGroup.getDescription());
                existing.setIsRequired(updatedGroup.getIsRequired());
                existing.setMaxSelections(updatedGroup.getMaxSelections());
                existing.setIsActive(updatedGroup.getIsActive());

                return modifierGroupRepository.save(existing);
    }

    public void deleteModifierGroup(Long id) {                              // Delete existing ModifierGroup
        modifierGroupRepository.deleteById(id);
    }
}
