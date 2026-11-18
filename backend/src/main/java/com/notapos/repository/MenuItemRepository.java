package com.notapos.repository;


import com.notapos.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<MenuItem> findByIsActive(Boolean isActive);        // Find all active items

    List<MenuItem> findByCategoryAndIsActive(String category, Boolean isActive);    // Find active items by category

    List<MenuItem> findByPrepStationId(Long prepStationId);     // Find items for specific prep stations

    List<MenuItem> findByIs86dAndIsActive(Boolean is86d, Boolean isActive);     // Find items on the menu that are (or are not) 86'd
}
