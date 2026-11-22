package com.notapos.service;


import org.springframework.transaction.annotation.Transactional;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.entity.MenuItem;
import com.notapos.repository.MenuItemRepository;
import com.notapos.security.StaffSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for MenuItem operations.
 * 
 * Contains business logic for managing menu items.
 * Sits between the Controller (REST API) and Repository (database).
 * 
 * @author CJ
 */

@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final NotificationService notificationService;      // Tells everyone else when an item is 86'd or back on

    @Autowired
    public MenuItemService(MenuItemRepository menuItemRepository, NotificationService notificationService) {
        this.menuItemRepository = menuItemRepository;
        this.notificationService = notificationService;
    }

    public List<MenuItem> getAllMenuItems() {       // Get all menu items (including inactive)
        return menuItemRepository.findAllByOrderByMenuItemIdAsc();      // Fixed order, so cards never move around on screen
    }

    public List<MenuItem> getActiveMenuItems() {            // Get all ACTIVE menu items
        return menuItemRepository.findByIsActiveOrderByMenuItemIdAsc(true);     // Fixed order, so cards never move around on screen
    }

    public Optional<MenuItem> getMenuItemById(Long id) {        // Get menu item by ID
        return menuItemRepository.findById(id);
    }
    
    public Optional<MenuItem> getMenuItemByName(String name) {  // Get menu item by name
        return menuItemRepository.findByName(name);
    }

    public List<MenuItem> getMenuItemsByCategory(String category) {     // Get all items in a category
        return menuItemRepository.findByCategory(category);
    }

    public List<MenuItem> getActiveMenuItemsByCategory(String category) {       // Get all active items in a category
        return menuItemRepository.findByCategoryAndIsActive(category, true);
    }

    public List<MenuItem> getMenuItemsByPrepStation(Long prepStationId) {       // Get all items for that prep station
        return menuItemRepository.findByPrepStationId(prepStationId);
    }

    @Transactional
    public MenuItem createMenuItem(MenuItem menuItem) {             // Create a new menu item
        return menuItemRepository.save(menuItem);
    }

    @Transactional
    public MenuItem updateMenuItem(Long id, MenuItem updatedItem) {     // Update existing menu item
        MenuItem existing = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id));

        existing.setName(updatedItem.getName());
        existing.setDescription(updatedItem.getDescription());
        existing.setPrice(updatedItem.getPrice());
        existing.setCategory(updatedItem.getCategory());
        existing.setPrepStationId(updatedItem.getPrepStationId());
        existing.setIsActive(updatedItem.getIsActive());

        return menuItemRepository.save(existing);
    }

    @Transactional
    public MenuItem deactivateMenuItem(Long id) {                       // "Soft delete" a menu item (turns it inactive)
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id));

        menuItem.setIsActive(false);
        return menuItemRepository.save(menuItem);
    }

    @Transactional
    public MenuItem activateMenuItem(Long id) {                         // Reactivate an inactive menu item
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id));

        menuItem.setIsActive(true);
        return menuItemRepository.save(menuItem);
    }

    public List<MenuItem> get86dMenuItems() {                           // Get everything on the menu that is 86'd right now
        return menuItemRepository.findByIs86dAndIsActiveOrderByMenuItemIdAsc(true, true);
    }

    /**
     * 86 an item (true) or put it back on (false), and tell everyone else.
     *
     * The notification is written in THIS transaction (NotificationService insists
     * on it), so the change and its notice are saved together or not at all. It is
     * only written when the item's state really changed: 86'ing something that is
     * already out tells nobody anything new.
     *
     * @param by The signed-in person making the change. They are not notified.
     */
    @Transactional
    public MenuItem set86d(Long id, boolean is86d, StaffSession by) {
        MenuItem menuItem = menuItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + id));

        boolean was86d = Boolean.TRUE.equals(menuItem.getIs86d());
        menuItem.setIs86d(is86d);
        MenuItem saved = menuItemRepository.save(menuItem);

        if (was86d != is86d) {
            notificationService.itemEightySixChanged(saved, by);
        }
        return saved;
    }

    @Transactional
    public void deleteMenuItem(Long id) {           // Completely delete item from repository (not reccomended, use deativateMenuItem() instead to preserve history)
        menuItemRepository.deleteById(id);
    }
}
