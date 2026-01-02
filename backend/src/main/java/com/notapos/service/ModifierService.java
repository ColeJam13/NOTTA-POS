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

    public List<Modifier> getAllModifier() {
        return modifierRepository.findAll();
    }

    public List<Modifier> getActiveModifiers() {
        return modifierRepository.findByIsActive(true);
    }

    public Optional<Modifier> getModifierById(Long id) {
        return modifierRepository.findById(id);
    }

    public List<Modifier> getModifiersByGroup(Long modifierGroupId) {
        return modifierRepository.findByModifierGroupIdAndIsActive(modifierGroupId, true);
    }

    public Modifier createModifier(Modifier modifier) {
        return modifierRepository.save(modifier);
    }

    public Modifier updateModifier(Long id, Modifier updatedModifier) {
        Modifier existing = modifierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Modifier not found with id: " + id));

        existing.setName(updatedModifier.getName());
        existing.setPriceAdjustment(updatedModifier.getPriceAdjustment());
        existing.setIsActive(updatedModifier.getIsActive());

        return modifierRepository.save(existing);
    }

    public void deleteModifier(Long id) {
        modifierRepository.deleteById(id);
    }
}
