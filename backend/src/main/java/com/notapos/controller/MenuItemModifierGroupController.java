package com.notapos.controller;


import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.service.MenuItemModifierGroupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for MenuItemModifierGroup operations.
 * 
 * Exposes HTTP endpoints for managing menu item and modifier group relationships.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/menu-item-modifier-groups")
public class MenuItemModifierGroupController {
    private final MenuItemModifierGroupService menuItemModifierGroupService;

    @Autowired
    public MenuItemModifierGroupController(MenuItemModifierGroupService menuItemModifierGroupService) {
        this.menuItemModifierGroupService = menuItemModifierGroupService;
    }

    @GetMapping
    public ResponseEntity<List<MenuItemModifierGroup>> getAllLinks() {                              //Get all links
        return ResponseEntity.ok(menuItemModifierGroupService.getAllLinks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MenuItemModifierGroup> getLinkById(@PathVariable Long id) {                   // Get Link by ID
        return menuItemModifierGroupService.getLinkById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/menu-item/{menuItemId}")
    public ResponseEntity<List<MenuItemModifierGroup>> getModifierGroupsForMenuItem(                            // Get modifier groups for menu item
            @PathVariable Long menuItemId) {
        return ResponseEntity.ok(menuItemModifierGroupService.getModifierGroupsForMenuItem(menuItemId));
    }

    @GetMapping("/modifier-group/{modifierGroupId}")
    public ResponseEntity<List<MenuItemModifierGroup>> getMenuItemsForModifierGroup(                            // Get menu items for modifier group
            @PathVariable Long modifierGroupId) {
                return ResponseEntity.ok(menuItemModifierGroupService.getMenuItemsForModifierGroup(modifierGroupId));
            }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PostMapping
    public ResponseEntity<MenuItemModifierGroup> createLink(@RequestBody MenuItemModifierGroup link) {          // Create new link
        MenuItemModifierGroup created = menuItemModifierGroupService.createLink(link);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLink(@PathVariable Long id) {                                     // Delete existing link
        menuItemModifierGroupService.deleteLink(id);
        return ResponseEntity.noContent().build();
    }
}