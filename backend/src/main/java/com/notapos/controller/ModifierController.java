package com.notapos.controller;

import com.notapos.entity.Modifier;
import com.notapos.service.ModifierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for Modifier operations.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/modifiers")
public class ModifierController {

    private final ModifierService modifierService;

    @Autowired
    public ModifierController(ModifierService modifierService) {
        this.modifierService = modifierService;
    }

    @GetMapping                                                                     // Get all Modifiers
    public ResponseEntity<List<Modifier>> getAllModifiers(
            @RequestParam(required = false) Boolean active) {

        if (active != null && active) {
            return ResponseEntity.ok(modifierService.getActiveModifiers());
        }
        return ResponseEntity.ok(modifierService.getAllModifiers());
    }

    @GetMapping("/{id}")                                                            // Get modifier by ID
    public ResponseEntity<Modifier> getModifierById(@PathVariable Long id) {
        return modifierService.getModifierById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/group/{modifierGroupId}")                                         // Get modifier by group
    public ResponseEntity<List<Modifier>> getModifiersByGroup(
            @PathVariable Long modifierGroupId,
            @RequestParam(required = false) Boolean active) {

        if (active != null && active) {
            return ResponseEntity.ok(modifierService.getActiveModifiersByGroup(modifierGroupId));
        }
        return ResponseEntity.ok(modifierService.getModifiersByGroup(modifierGroupId));
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PostMapping                                                                            // Create new modifier
    public ResponseEntity<Modifier> createModifier(@RequestBody Modifier modifier) {
        Modifier created = modifierService.createModifier(modifier);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PutMapping("/{id}")                                            // Update existing modifier
    public ResponseEntity<Modifier> updateModifier(
            @PathVariable Long id,
            @RequestBody Modifier modifier) {
        Modifier updated = modifierService.updateModifier(id, modifier);
        return ResponseEntity.ok(updated);
    }
    
    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @DeleteMapping("/{id}")                                                     // Delete existing modifier
    public ResponseEntity<Void> deleteModifier(@PathVariable Long id) {
        modifierService.deleteModifier(id);
        return ResponseEntity.noContent().build();
    }
}
