package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.repository.MenuItemModifierGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for MenuItemModifierGroup operations.
 * 
 * Contains business logic for managing menu item and modifier group relationships.
 * 
 * @author CJ
 */

@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class MenuItemModifierGroupService {
    
    private final MenuItemModifierGroupRepository menuItemModifierGroupRepository;

    @Autowired
    public MenuItemModifierGroupService(MenuItemModifierGroupRepository menuItemModifierGroupRepository) {
        this.menuItemModifierGroupRepository = menuItemModifierGroupRepository;
    }

    public List<MenuItemModifierGroup> getAllLinks() {                                  // Get all menu item modifier group links
        return menuItemModifierGroupRepository.findAll();
    }

    public Optional<MenuItemModifierGroup> getLinkById(Long id) {                               // Get item modifier group link by ID
        return menuItemModifierGroupRepository.findById(id);
    }

    public List<MenuItemModifierGroup> getModifierGroupsForMenuItem(Long menuItemId) {          // Get all modifier groups for a menu item
        return menuItemModifierGroupRepository.findByMenuItemId(menuItemId);
    }

    public List<MenuItemModifierGroup> getMenuItemsForModifierGroup(Long modifierGroupId) {     // Get all menu items that use a specific modifier group
        return menuItemModifierGroupRepository.findByModifierGroupId(modifierGroupId);
    }

    @Transactional
    public MenuItemModifierGroup createLink(MenuItemModifierGroup link) {                       // Create a new link between menu item and modifier group
        return menuItemModifierGroupRepository.save(link);
    }

    @Transactional
    public void deleteLink(Long id) {
        menuItemModifierGroupRepository.deleteById(id);                                         // Delete a link
    }
}
