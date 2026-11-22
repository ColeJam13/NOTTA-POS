package com.notapos.repository;


import com.notapos.entity.MenuItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;


/**
 * Repository for MenuItem entity.
 * 
 * Provides database access methods for managing menu items.
 * Spring Data JPA auto-generates implementation at runtime.
 * 
 * @author CJ
 */

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    Optional<MenuItem> findByName(String name);             // Find item by name

    List<MenuItem> findByCategory(String category);         // Find item by category
    boolean existsByCategory(String category);              // Is any item (on the menu or off it) in this category?

    List<MenuItem> findByIsActive(Boolean isActive);        // Find all active items

    List<MenuItem> findByCategoryAndIsActive(String category, Boolean isActive);    // Find active items by category

    List<MenuItem> findByPrepStationId(Long prepStationId);     // Find items for specific prep stations

    List<MenuItem> findByIs86dAndIsActiveOrderByMenuItemIdAsc(Boolean is86d, Boolean isActive);     // Find items on the menu that are (or are not) 86'd

    // The same lists as findAll() and findByIsActive(), in a fixed order (by ID).
    // Without ORDER BY, PostgreSQL returns rows in whatever order they sit on disk, and
    // a row that was just updated moves to the end. 86'ing an item made its card jump to
    // the bottom of its category on the order screen.
    List<MenuItem> findAllByOrderByMenuItemIdAsc();

    List<MenuItem> findByIsActiveOrderByMenuItemIdAsc(Boolean isActive);

    /**
     * Items on the menu whose name contains what was typed, by name, for the search box.
     * An 86'd item is still on the menu and is found; an inactive one is not.
     *
     * @param pattern See TableRepository.searchRegularTables: a parameter, never query text
     * @param page    How many to return at most
     */
    @Query("SELECT m FROM MenuItem m WHERE m.isActive = true AND LOWER(m.name) LIKE :pattern ESCAPE '\\' ORDER BY m.name, m.menuItemId")
    List<MenuItem> searchOnTheMenu(String pattern, Pageable page);
}
