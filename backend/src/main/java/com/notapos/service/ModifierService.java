package com.notapos.service;

import com.notapos.entity.Modifier;
import com.notapos.repository.ModifierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Modifier operations.
 * 
 * @author CJ
 */

@Service
public class ModifierService {
    
    private final ModifierRepository modifierRepository;

    @Autowired
    public ModifierService(ModifierRepository modifierRepository) {
        this.modifierRepository = modifierRepository;
    }

    public List<Modifier> getAllModifiers() {                           // Get all Modififers
        return modifierRepository.findAll();
    }

    public List<Modifier> getActiveModifiers() {                        // Get all active Modifiers
        return modifierRepository.findByIsActive(true);
    }

    public Optional<Modifier> getModifierById(Long id) {                // Get all Modifers 
        return modifierRepository.findById(id);
    }

    public List<Modifier> getModifiersByGroup(Long modifierGroupId) {               // Get modifiers by group
        return modifierRepository.findByModifierGroupId(modifierGroupId);
    }

    public List<Modifier> getActiveModifiersByGroup(Long modifierGroupId) {                     // Get active Modifiers by group
        return modifierRepository.findByModifierGroupIdAndIsActive(modifierGroupId, true);
    }

    public Modifier createModifier(Modifier modifier) {                                             // Create new modifier
        return modifierRepository.save(modifier);
    }

    public Modifier updateModifier(Long id, Modifier updatedModifier) {                             // Update existing Modifier
        Modifier existing = modifierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modifier not found with id: " + id));

        existing.setName(updatedModifier.getName());
        existing.setPriceAdjustment(updatedModifier.getPriceAdjustment());
        existing.setIsActive(updatedModifier.getIsActive());

        return modifierRepository.save(existing);
    }

    public void deleteModifier(Long id) {                                           // Delete existing modifier
        modifierRepository.deleteById(id);
    }
}
