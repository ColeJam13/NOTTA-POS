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

    public List<ModifierGroup> getAllModifierGroups() {
        return modifierGroupRepository.findAll();
    }

    public List<ModifierGroup> getActiveModifierGroups() {
        return modifierGroupRepository.findByIsActive(true);
    }

    public Optional<ModifierGroup> getModifierGroupById(Long id) {
        return modifierGroupRepository.findById(id);
    }

    public Optional<ModifierGroup> getModifierGroupByName(String name) {
        return modifierGroupRepository.findByName(name);
    }

    public ModifierGroup createModifierGroup(ModifierGroup modifierGroup) {
        return modifierGroupRepository.save(modifierGroup);
    }

    public ModifierGroup updateModifierGroup(Long id, ModifierGroup updatedGroup) {
        ModifierGroup existing = modifierGroupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modifier group not found with id: " +id));

                existing.setName(updatedGroup.getName());
                existing.setDescription(updatedGroup.getDescription());
                existing.setIsRequired(updatedGroup.getIsRequired());
                existing.setMaxSelections(updatedGroup.getMaxSelections());
                existing.setIsActive(updatedGroup.getIsActive());

                return modifierGroupRepository.save(existing);
    }

    public void deleteModifierGroup(Long id) {
        modifierGroupRepository.deleteById(id);
    }
}
