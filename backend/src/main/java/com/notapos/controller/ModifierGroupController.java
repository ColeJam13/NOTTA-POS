package com.notapos.controller;

import com.notapos.entity.ModifierGroup;
import com.notapos.service.ModifierGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for ModifierGroup operations.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/modifier-groups")
public class ModifierGroupController {
    
    private final ModifierGroupService modifierGroupService;

    @Autowired
    public ModifierGroupController(ModifierGroupService modifierGroupService) {
        this.modifierGroupService = modifierGroupService;
    }

    @GetMapping                                                                         // Get all modifier groups
    public ResponseEntity<List<ModifierGroup>> getAllModifierGroups(
            @RequestParam(required = false) Boolean active) {

        if (active != null && active) {
            return ResponseEntity.ok(modifierGroupService.getActiveModifierGroups());
        }
        return ResponseEntity.ok(modifierGroupService.getAllModifierGroups());
    }

    @GetMapping("/{id}")                                                                        // Get modifier group by ID
    public ResponseEntity<ModifierGroup> getModifierGroupById(@PathVariable Long id) {
        return modifierGroupService.getModifierGroupById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PostMapping                                                                                            // Create new modifier group
    public ResponseEntity<ModifierGroup> createModifierGroup(@RequestBody ModifierGroup modifierGroup) {
        ModifierGroup created = modifierGroupService.createModifierGroup(modifierGroup);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PutMapping("/{id}")                                                                // Update existing modifier group
    public ResponseEntity<ModifierGroup> updateModifierGroup(
            @PathVariable Long id,
            @RequestBody ModifierGroup modifierGroup) {
        ModifierGroup updated = modifierGroupService.updateModifierGroup(id, modifierGroup);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @DeleteMapping("/{id}")                                                             // Delete existing modifier group
    public ResponseEntity<Void> deleteModifierGroup(@PathVariable Long id) {
        modifierGroupService.deleteModifierGroup(id);
        return ResponseEntity.noContent().build();
    }
}
