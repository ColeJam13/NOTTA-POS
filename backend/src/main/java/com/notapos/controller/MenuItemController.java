package com.notapos.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import com.notapos.entity.MenuItem;
import com.notapos.service.MenuItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.notapos.security.Permissions;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * REST API Controller for MenuItem operations.
 * 
 * Exposes HTTP endpoints for managing menu items.
 * 
 * @author CJ
 */

@RestController
@RequestMapping("/api/menu-items")
public class MenuItemController {

    private final MenuItemService menuItemService;

    @Autowired
    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @GetMapping                                                         // Get all menu items
    public ResponseEntity<List<MenuItem>> getAllMenuItems(
            @RequestParam(required = false) Boolean active) {

        if (active != null && active) {
            return ResponseEntity.ok(menuItemService.getActiveMenuItems());
        }

        return ResponseEntity.ok(menuItemService.getActiveMenuItems());
    }
    
    @GetMapping("/{id}")                                                         // Get menu item by id
    public ResponseEntity<MenuItem> getMenuItemById(@PathVariable Long id) {
        return menuItemService.getMenuItemById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")                                         // Get menu items by category
    public ResponseEntity<List<MenuItem>> getMenuItemByCategory(
        @PathVariable String category,
        @RequestParam(required = false) Boolean active) {

    if (active != null && active) {
        return ResponseEntity.ok(
            menuItemService.getActiveMenuItemsByCategory(category));
    }
    return ResponseEntity.ok(
        menuItemService.getMenuItemsByCategory(category));
    }

    @GetMapping("/prep-station/{prepStationId}")                                // Get menu items by prep station
    public ResponseEntity<List<MenuItem>> getMenuItemsByPrepStation(
            @PathVariable Long prepStationId) {
        List<MenuItem> items = menuItemService.getMenuItemsByPrepStation(prepStationId);
        return ResponseEntity.ok(items);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PostMapping                                                                        // Create new menu item
    public ResponseEntity<MenuItem> createMenuItem(@Valid @RequestBody MenuItem menuItem) {
        MenuItem created = menuItemService.createMenuItem(menuItem);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PutMapping("/{id}")                                                        // Update menu item
    public ResponseEntity<MenuItem> updateMenuItem(
            @PathVariable Long id,
            @RequestBody MenuItem menuItem) {
        MenuItem updated = menuItemService.updateMenuItem(id, menuItem);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PutMapping("/{id}/deactivate")                                                 // Deactivate (mark inactive/soft delete) menu item
    public ResponseEntity<MenuItem> deactivateMenuItem(@PathVariable Long id) {
        MenuItem deactivated = menuItemService.deactivateMenuItem(id);
        return ResponseEntity.ok(deactivated);
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @PutMapping("/{id}/activate")                                                   // Reactivate inactive item
    public ResponseEntity<MenuItem> activateMenuItem(@PathVariable Long id) {
        MenuItem activated = menuItemService.activateMenuItem(id);
        return ResponseEntity.ok(activated);
    }

    @GetMapping("/86")                                                              // Get everything that is 86'd right now
    public ResponseEntity<List<MenuItem>> get86dMenuItems() {
        return ResponseEntity.ok(menuItemService.get86dMenuItems());
    }

    @PreAuthorize(Permissions.CHANGE_86)             // The people who know what has run out
    @PutMapping("/{id}/86")                                                         // 86 an item: out for now, still shown on the menu
    public ResponseEntity<MenuItem> eightySixMenuItem(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.set86d(id, true));
    }

    @PreAuthorize(Permissions.CHANGE_86)
    @PutMapping("/{id}/un-86")                                                      // Put an 86'd item back on
    public ResponseEntity<MenuItem> unEightySixMenuItem(@PathVariable Long id) {
        return ResponseEntity.ok(menuItemService.set86d(id, false));
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)                                     // Managers only
    @DeleteMapping("/{id}")                                                         // Delete menu item
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.noContent().build();
    }
}

